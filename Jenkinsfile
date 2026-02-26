pipeline {
    agent any

    tools {
        jdk 'JDK21'
        maven 'Maven3'
    }

    environment {
        GITHUB_TOKEN = credentials('github-token')
        REPO         = 'ATOMGAMERAGA/AtomGuard'
    }

    options {
        skipDefaultCheckout(false)
        timestamps()
        timeout(time: 15, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    stages {

        // ─────────────────────────────────────────────
        //  1. CHECKOUT
        // ─────────────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // ─────────────────────────────────────────────
        //  2. SÜRÜM & GIT BİLGİLERİNİ ÇEK
        // ─────────────────────────────────────────────
        stage('Resolve Version') {
            steps {
                script {
                    // ── pom.xml'den base versiyon ──
                    env.BASE_VERSION = sh(
                        script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout",
                        returnStdout: true
                    ).trim()

                    // ── Git bilgileri ──
                    env.GIT_COMMIT_SHORT = sh(
                        script: "git rev-parse --short=7 HEAD",
                        returnStdout: true
                    ).trim()

                    env.GIT_COMMIT_MSG = sh(
                        script: "git log -1 --pretty=%s",
                        returnStdout: true
                    ).trim()

                    env.GIT_COMMIT_AUTHOR = sh(
                        script: "git log -1 --pretty=%an",
                        returnStdout: true
                    ).trim()

                    env.GIT_COMMIT_DATE = sh(
                        script: "git log -1 --pretty=%ci",
                        returnStdout: true
                    ).trim()

                    // ── Commit sayısı (build numarası olarak) ──
                    env.COMMIT_COUNT = sh(
                        script: "git rev-list --count HEAD",
                        returnStdout: true
                    ).trim()

                    // ── Bu commit bir tag mi? ──
                    def tagCheck = sh(
                        script: "git describe --exact-match --tags HEAD 2>/dev/null || echo ''",
                        returnStdout: true
                    ).trim()

                    env.IS_TAG = tagCheck.startsWith('v') ? 'true' : 'false'
                    env.GIT_TAG = tagCheck

                    // ── Branch adı ──
                    env.BRANCH_NAME_CLEAN = sh(
                        script: "echo '${env.BRANCH_NAME ?: env.GIT_BRANCH}' | sed 's|origin/||' | sed 's|/|-|g'",
                        returnStdout: true
                    ).trim()

                    // ── Release türünü belirle ──
                    if (env.IS_TAG == 'true') {
                        env.RELEASE_TYPE    = 'stable'
                        env.RELEASE_VERSION = env.BASE_VERSION
                        env.TAG_NAME        = "v${env.BASE_VERSION}"
                        env.RELEASE_TITLE   = "AtomGuard v${env.BASE_VERSION}"
                    } else if (env.BRANCH_NAME_CLEAN == 'main' || env.BRANCH_NAME_CLEAN == 'master') {
                        env.RELEASE_TYPE    = 'dev'
                        env.RELEASE_VERSION = "${env.BASE_VERSION}-dev.${env.COMMIT_COUNT}"
                        env.TAG_NAME        = "v${env.BASE_VERSION}-dev.${env.COMMIT_COUNT}"
                        env.RELEASE_TITLE   = "AtomGuard v${env.BASE_VERSION} \u2014 Dev Build #${env.COMMIT_COUNT}"
                    } else {
                        env.RELEASE_TYPE    = 'none'
                        env.RELEASE_VERSION = "${env.BASE_VERSION}-${env.BRANCH_NAME_CLEAN}.${env.COMMIT_COUNT}"
                        env.TAG_NAME        = ''
                        env.RELEASE_TITLE   = ''
                    }

                    echo """
                    ╔══════════════════════════════════════════════════╗
                    ║           AtomGuard Build Information            ║
                    ╠══════════════════════════════════════════════════╣
                    ║  Base Version  : ${env.BASE_VERSION}
                    ║  Release Type  : ${env.RELEASE_TYPE}
                    ║  Full Version  : ${env.RELEASE_VERSION}
                    ║  Tag           : ${env.TAG_NAME ?: 'N/A'}
                    ║  Branch        : ${env.BRANCH_NAME_CLEAN}
                    ║  Commit        : ${env.GIT_COMMIT_SHORT}
                    ║  Author        : ${env.GIT_COMMIT_AUTHOR}
                    ║  Message       : ${env.GIT_COMMIT_MSG}
                    ╚══════════════════════════════════════════════════╝
                    """
                }
            }
        }

        // ─────────────────────────────────────────────
        //  3. BUILD
        // ─────────────────────────────────────────────
        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests -B -q'
            }
        }

        // ─────────────────────────────────────────────
        //  4. ARTIFACT DOĞRULAMA
        // ─────────────────────────────────────────────
        stage('Verify Artifacts') {
            steps {
                script {
                    env.CORE_JAR = sh(
                        script: "find core/target -maxdepth 1 -name 'AtomGuard-core-*.jar' -not -name '*-sources*' -not -name '*original*' | head -1",
                        returnStdout: true
                    ).trim()

                    env.VELOCITY_JAR = sh(
                        script: "find velocity/target -maxdepth 1 -name 'AtomGuard-velocity-*.jar' -not -name '*-sources*' -not -name '*original*' | head -1",
                        returnStdout: true
                    ).trim()

                    env.API_JAR = sh(
                        script: "find api/target -maxdepth 1 -name 'AtomGuard-api-*.jar' -not -name '*-sources*' -not -name '*original*' | head -1",
                        returnStdout: true
                    ).trim()

                    if (!env.CORE_JAR || !env.VELOCITY_JAR) {
                        error "❌ JAR dosyaları bulunamadı! Build başarısız."
                    }

                    sh """
                        echo "✅ Artifact'lar doğrulandı:"
                        echo "   Core     : ${env.CORE_JAR} (\$(du -h ${env.CORE_JAR} | cut -f1))"
                        echo "   Velocity : ${env.VELOCITY_JAR} (\$(du -h ${env.VELOCITY_JAR} | cut -f1))"
                        echo "   API      : ${env.API_JAR} (\$(du -h ${env.API_JAR} | cut -f1))"
                    """
                }
            }
        }

        // ─────────────────────────────────────────────
        //  5. JAR'LARI YENİDEN ADLANDIR
        // ─────────────────────────────────────────────
        stage('Rename Artifacts') {
            when {
                expression { env.RELEASE_TYPE != 'none' }
            }
            steps {
                script {
                    def ver = env.RELEASE_VERSION

                    env.CORE_RELEASE     = "AtomGuard-Core-${ver}.jar"
                    env.VELOCITY_RELEASE = "AtomGuard-Velocity-${ver}.jar"
                    env.API_RELEASE      = "AtomGuard-API-${ver}.jar"

                    sh """
                        mkdir -p release-artifacts
                        cp ${env.CORE_JAR}     release-artifacts/${env.CORE_RELEASE}
                        cp ${env.VELOCITY_JAR} release-artifacts/${env.VELOCITY_RELEASE}
                        cp ${env.API_JAR}      release-artifacts/${env.API_RELEASE}
                    """
                }
            }
        }

        // ─────────────────────────────────────────────
        //  6. CHECKSUM OLUŞTUR
        // ─────────────────────────────────────────────
        stage('Generate Checksums') {
            when {
                expression { env.RELEASE_TYPE != 'none' }
            }
            steps {
                sh """
                    cd release-artifacts
                    sha256sum *.jar > SHA256SUMS.txt
                    echo "🔐 Checksums:"
                    cat SHA256SUMS.txt
                """
            }
        }

        // ─────────────────────────────────────────────
        //  7. RELEASE NOTES OLUŞTUR
        // ─────────────────────────────────────────────
        stage('Generate Release Notes') {
            when {
                expression { env.RELEASE_TYPE != 'none' }
            }
            steps {
                script {
                    // CHANGELOG.md'den ilgili versiyonun notlarını çek
                    env.CHANGELOG_NOTES = sh(
                        script: """
                            awk -v ver="${env.BASE_VERSION}" '
                                /^## \\[/ {
                                    if (found) exit
                                    if (index(\$0, "[" ver "]") > 0) { found=1; next }
                                }
                                found { print }
                            ' CHANGELOG.md 2>/dev/null || echo ""
                        """,
                        returnStdout: true
                    ).trim()

                    // Son 10 commit
                    env.RECENT_COMMITS = sh(
                        script: """
                            git log --oneline -10 --pretty=format:'- [\`%h\`](https://github.com/${env.REPO}/commit/%H) %s (%an)' 2>/dev/null || echo ""
                        """,
                        returnStdout: true
                    ).trim()

                    // ── STABLE RELEASE ──
                    if (env.RELEASE_TYPE == 'stable') {
                        def notes = """## 🛡️ AtomGuard v${env.BASE_VERSION}

**Advanced Minecraft Server Security & Exploit Protection**

---

### 📦 Kurulum

| Platform | Dosya | Hedef Klasör |
|----------|-------|-------------|
| Paper / Spigot | \`${env.CORE_RELEASE}\` | \`plugins/\` |
| Velocity Proxy | \`${env.VELOCITY_RELEASE}\` | \`plugins/\` |
| API (Geliştirici) | \`${env.API_RELEASE}\` | Maven dependency |

1. Sunucuyu durdur
2. Eski AtomGuard JAR dosyalarını sil
3. Yeni JAR'ları ilgili klasörlere koy
4. Sunucuyu başlat

### 📋 Değişiklikler

${env.CHANGELOG_NOTES ?: '_Bu sürüm için changelog girilmemiş._'}

### 🔒 Doğrulama

İndirdiğiniz dosyaların bütünlüğünü kontrol edin:
```bash
sha256sum -c SHA256SUMS.txt
```

---
🔧 Build #${env.BUILD_NUMBER} | Java 21 | Commit [\`${env.GIT_COMMIT_SHORT}\`](https://github.com/${env.REPO}/commit/${env.GIT_COMMIT_SHORT})"""

                        writeFile file: 'release-artifacts/RELEASE_NOTES.md', text: notes

                    // ── DEV BUILD ──
                    } else {
                        def notes = """## 🔧 AtomGuard v${env.BASE_VERSION} — Dev Build #${env.COMMIT_COUNT}

> ⚠️ **Bu bir geliştirme sürümüdür.** Kararlı sürüm değildir, test amaçlıdır.

### 📦 Dosyalar

| Platform | Dosya |
|----------|-------|
| Paper / Spigot | \`${env.CORE_RELEASE}\` |
| Velocity Proxy | \`${env.VELOCITY_RELEASE}\` |

### 📝 Son Değişiklikler

${env.RECENT_COMMITS ?: '_Commit bilgisi alınamadı._'}

---
🔧 Build #${env.BUILD_NUMBER} | Branch: \`${env.BRANCH_NAME_CLEAN}\` | Commit [\`${env.GIT_COMMIT_SHORT}\`](https://github.com/${env.REPO}/commit/${env.GIT_COMMIT_SHORT}) | ${env.GIT_COMMIT_DATE}"""

                        writeFile file: 'release-artifacts/RELEASE_NOTES.md', text: notes
                    }
                }
            }
        }

        // ─────────────────────────────────────────────
        //  8. GITHUB CLI KURULUMU
        // ─────────────────────────────────────────────
        stage('Setup GitHub CLI') {
            when {
                expression { env.RELEASE_TYPE != 'none' }
            }
            steps {
                sh '''
                    if command -v gh &> /dev/null; then
                        echo "✅ GitHub CLI: $(gh --version | head -1)"
                    else
                        echo "📥 GitHub CLI kuruluyor..."
                        (type -p wget >/dev/null || (apt-get update && apt-get install wget -y)) \
                        && mkdir -p -m 755 /etc/apt/keyrings \
                        && wget -qO- https://cli.github.com/packages/githubcli-archive-keyring.gpg \
                            | tee /etc/apt/keyrings/githubcli-archive-keyring.gpg > /dev/null \
                        && chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg \
                        && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
                            | tee /etc/apt/sources.list.d/github-cli.list > /dev/null \
                        && apt-get update && apt-get install gh -y
                        echo "✅ GitHub CLI kuruldu: $(gh --version | head -1)"
                    fi
                '''
            }
        }

        // ─────────────────────────────────────────────
        //  9. STABLE RELEASE (tag push)
        // ─────────────────────────────────────────────
        stage('Publish Stable Release') {
            when {
                expression { env.RELEASE_TYPE == 'stable' }
            }
            steps {
                script {
                    sh """
                        export GH_TOKEN=\${GITHUB_TOKEN}

                        echo "🚀 Stable Release: ${env.TAG_NAME}"

                        # Mevcut release varsa sil
                        gh release delete ${env.TAG_NAME} --repo ${env.REPO} --yes 2>/dev/null || true

                        # Aynı base version'daki tüm dev build'leri temizle
                        echo "🧹 Dev build'ler temizleniyor..."
                        gh release list --repo ${env.REPO} --limit 100 2>/dev/null \
                            | grep -oP "v${env.BASE_VERSION}-dev\\.\\d+" \
                            | while read devtag; do
                                echo "   🗑️ Siliniyor: \$devtag"
                                gh release delete "\$devtag" --repo ${env.REPO} --yes 2>/dev/null || true
                                git push origin :refs/tags/"\$devtag" 2>/dev/null || true
                            done

                        # Release oluştur
                        gh release create ${env.TAG_NAME} \
                            --repo ${env.REPO} \
                            --title "${env.RELEASE_TITLE}" \
                            --notes-file release-artifacts/RELEASE_NOTES.md \
                            --latest \
                            release-artifacts/${env.CORE_RELEASE} \
                            release-artifacts/${env.VELOCITY_RELEASE} \
                            release-artifacts/${env.API_RELEASE} \
                            release-artifacts/SHA256SUMS.txt
                    """

                    echo "✅ https://github.com/${env.REPO}/releases/tag/${env.TAG_NAME}"
                }
            }
        }

        // ─────────────────────────────────────────────
        //  10. DEV BUILD RELEASE (main commit)
        // ─────────────────────────────────────────────
        stage('Publish Dev Build') {
            when {
                expression { env.RELEASE_TYPE == 'dev' }
            }
            steps {
                script {
                    sh """
                        export GH_TOKEN=\${GITHUB_TOKEN}

                        echo "🔧 Dev Build: ${env.TAG_NAME}"

                        # Eski dev build'leri temizle — son 5 hariç
                        gh release list --repo ${env.REPO} --limit 50 2>/dev/null \
                            | grep -oP "v${env.BASE_VERSION}-dev\\.\\d+" \
                            | tail -n +6 \
                            | while read oldtag; do
                                echo "   🗑️ Eski build siliniyor: \$oldtag"
                                gh release delete "\$oldtag" --repo ${env.REPO} --yes 2>/dev/null || true
                                git push origin :refs/tags/"\$oldtag" 2>/dev/null || true
                            done

                        # Aynı tag varsa sil
                        gh release delete ${env.TAG_NAME} --repo ${env.REPO} --yes 2>/dev/null || true
                        git push origin :refs/tags/${env.TAG_NAME} 2>/dev/null || true

                        # Pre-release olarak oluştur
                        gh release create ${env.TAG_NAME} \
                            --repo ${env.REPO} \
                            --title "${env.RELEASE_TITLE}" \
                            --notes-file release-artifacts/RELEASE_NOTES.md \
                            --prerelease \
                            release-artifacts/${env.CORE_RELEASE} \
                            release-artifacts/${env.VELOCITY_RELEASE} \
                            release-artifacts/${env.API_RELEASE} \
                            release-artifacts/SHA256SUMS.txt
                    """

                    echo "✅ https://github.com/${env.REPO}/releases/tag/${env.TAG_NAME}"
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    //  POST ACTIONS
    // ─────────────────────────────────────────────
    post {
        success {
            script {
                def emoji = env.RELEASE_TYPE == 'stable' ? '🚀' : (env.RELEASE_TYPE == 'dev' ? '🔧' : '✅')
                def releaseUrl = env.TAG_NAME ? "https://github.com/${env.REPO}/releases/tag/${env.TAG_NAME}" : 'N/A'
                echo """
                ╔═══════════════════════════════════════════════════╗
                ║  ${emoji} AtomGuard BUILD SUCCESS
                ╠═══════════════════════════════════════════════════╣
                ║  Version  : ${env.RELEASE_VERSION}
                ║  Type     : ${env.RELEASE_TYPE}
                ║  Commit   : ${env.GIT_COMMIT_SHORT} — ${env.GIT_COMMIT_MSG}
                ║  Release  : ${releaseUrl}
                ╚═══════════════════════════════════════════════════╝
                """
            }
        }
        failure {
            echo """
            ╔═══════════════════════════════════════════════════╗
            ║  ❌ AtomGuard BUILD FAILED
            ╠═══════════════════════════════════════════════════╣
            ║  Version  : ${env.BASE_VERSION ?: 'N/A'}
            ║  Commit   : ${env.GIT_COMMIT_SHORT ?: 'N/A'}
            ║  Branch   : ${env.BRANCH_NAME_CLEAN ?: 'N/A'}
            ╚═══════════════════════════════════════════════════╝
            """
        }
        always {
            cleanWs()
        }
    }
}
