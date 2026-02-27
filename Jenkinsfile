pipeline {
    agent any

    tools {
        jdk 'JDK21'
        maven 'Maven3'
    }

    environment {
        GITHUB_TOKEN   = credentials('github-token')
        MODRINTH_TOKEN = credentials('modrinth-token')
        REPO           = 'ATOMGAMERAGA/AtomGuard'
        MODRINTH_ID    = credentials('modrinth-project-id')
    }

    options {
        skipDefaultCheckout(false)
        timeout(time: 15, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    stages {

        // ═════════════════════════════════════════════
        //  1. CHECKOUT
        // ═════════════════════════════════════════════
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // ═════════════════════════════════════════════
        //  2. SÜRÜM & GIT BİLGİLERİ
        // ═════════════════════════════════════════════
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

                    // ── Commit sayısı ──
                    env.COMMIT_COUNT = sh(
                        script: "git rev-list --count HEAD",
                        returnStdout: true
                    ).trim()

                    // ── Tag kontrolü ──
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

                    // ── Release türü ──
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

        // ═════════════════════════════════════════════
        //  3. BUILD
        // ═════════════════════════════════════════════
        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests -B -q'
            }
        }

        // ═════════════════════════════════════════════
        //  4. ARTIFACT DOĞRULAMA
        // ═════════════════════════════════════════════
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
                        error "❌ JAR dosyaları bulunamadı!"
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

        // ═════════════════════════════════════════════
        //  5. ARTIFACT'LARI YENİDEN ADLANDIR
        // ═════════════════════════════════════════════
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

        // ═════════════════════════════════════════════
        //  6. CHECKSUM
        // ═════════════════════════════════════════════
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

        // ═════════════════════════════════════════════
        //  7. RELEASE NOTES
        // ═════════════════════════════════════════════
        stage('Generate Release Notes') {
            when {
                expression { env.RELEASE_TYPE != 'none' }
            }
            steps {
                script {
                    // CHANGELOG.md'den notları çek
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
                            git log --oneline -10 --pretty=format:'- [`%h`](https://github.com/${env.REPO}/commit/%H) %s (%an)' 2>/dev/null || echo ""
                        """,
                        returnStdout: true
                    ).trim()

                    // ── Modrinth changelog (Markdown) ──
                    if (env.RELEASE_TYPE == 'stable') {
                        env.MODRINTH_CHANGELOG = env.CHANGELOG_NOTES ?: "AtomGuard v${env.BASE_VERSION} yayınlandı."
                    } else {
                        env.MODRINTH_CHANGELOG = "**Dev Build #${env.COMMIT_COUNT}**\n\n${env.RECENT_COMMITS ?: 'Geliştirme sürümü.'}"
                    }

                    // ── GitHub Release Notes ──
                    if (env.RELEASE_TYPE == 'stable') {
                        def notes = """## 🛡️ AtomGuard v${env.BASE_VERSION}

**Advanced Minecraft Server Security & Exploit Protection**

---

### 📦 Kurulum

| Platform | Dosya | Hedef Klasör |
|----------|-------|-------------|
| Paper / Spigot | `${env.CORE_RELEASE}` | `plugins/` |
| Velocity Proxy | `${env.VELOCITY_RELEASE}` | `plugins/` |
| API (Geliştirici) | `${env.API_RELEASE}` | Maven dependency |

1. Sunucuyu durdur
2. Eski AtomGuard JAR dosyalarını sil
3. Yeni JAR'ları ilgili klasörlere koy
4. Sunucuyu başlat

### 📋 Değişiklikler

${env.CHANGELOG_NOTES ?: '_Bu sürüm için changelog girilmemiş._'}

### 🔒 Doğrulama
```bash
sha256sum -c SHA256SUMS.txt
```

---
🔧 Build #${env.BUILD_NUMBER} | Java 21 | Commit [`${env.GIT_COMMIT_SHORT}`](https://github.com/${env.REPO}/commit/${env.GIT_COMMIT_SHORT})
📦 [Modrinth](https://modrinth.com/plugin/atomguard)"""

                        writeFile file: 'release-artifacts/RELEASE_NOTES.md', text: notes

                    } else {
                        def notes = """## 🔧 AtomGuard v${env.BASE_VERSION} — Dev Build #${env.COMMIT_COUNT}

> ⚠️ **Bu bir geliştirme sürümüdür.** Kararlı sürüm değildir, test amaçlıdır.

### 📦 Dosyalar

| Platform | Dosya |
|----------|-------|
| Paper / Spigot | `${env.CORE_RELEASE}` |
| Velocity Proxy | `${env.VELOCITY_RELEASE}` |

### 📝 Son Değişiklikler

${env.RECENT_COMMITS ?: '_Commit bilgisi alınamadı._'}

---
🔧 Build #${env.BUILD_NUMBER} | Branch: `${env.BRANCH_NAME_CLEAN}` | Commit [`${env.GIT_COMMIT_SHORT}`](https://github.com/${env.REPO}/commit/${env.GIT_COMMIT_SHORT})"""

                        writeFile file: 'release-artifacts/RELEASE_NOTES.md', text: notes
                    }
                }
            }
        }

        // ═════════════════════════════════════════════
        //  8. GITHUB — STABLE RELEASE (curl/python3)
        // ═════════════════════════════════════════════
        stage('GitHub: Stable Release') {
            when {
                expression { env.RELEASE_TYPE == 'stable' }
            }
            steps {
                script {
                    sh """
                        echo "🚀 GitHub Stable Release: ${env.TAG_NAME}"

                        # Mevcut release varsa sil
                        OLD_ID=\$(curl -sf -H "Authorization: token \${GITHUB_TOKEN}" \
                            "https://api.github.com/repos/${env.REPO}/releases/tags/${env.TAG_NAME}" \
                            | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('id',''))" 2>/dev/null || echo "")
                        if [ -n "\$OLD_ID" ] && [ "\$OLD_ID" != "None" ]; then
                            curl -sf -X DELETE -H "Authorization: token \${GITHUB_TOKEN}" \
                                "https://api.github.com/repos/${env.REPO}/releases/\$OLD_ID" || true
                            echo "   Eski release silindi: \$OLD_ID"
                        fi

                        # Aynı base version dev build'lerini temizle
                        curl -sf -H "Authorization: token \${GITHUB_TOKEN}" \
                            "https://api.github.com/repos/${env.REPO}/releases?per_page=50" 2>/dev/null \
                            | python3 -c "
import json, sys, subprocess
releases = json.load(sys.stdin)
for r in releases:
    tag = r.get('tag_name','')
    if tag.startswith('v${env.BASE_VERSION}-dev.'):
        rid = r['id']
        print('   Dev build siliniyor: ' + tag)
        subprocess.run(['curl','-sf','-X','DELETE','-H','Authorization: token \${GITHUB_TOKEN}',
            'https://api.github.com/repos/${env.REPO}/releases/'+str(rid)], capture_output=True)
" 2>/dev/null || true

                        # Release JSON oluştur
                        python3 -c "
import json
notes = open('release-artifacts/RELEASE_NOTES.md').read()
data = {'tag_name':'${env.TAG_NAME}','name':'${env.RELEASE_TITLE}','body':notes,'draft':False,'prerelease':False,'make_latest':'true'}
open('/tmp/gh_release_payload.json','w').write(json.dumps(data))
print('Payload hazırlandı')
"
                        # Release oluştur
                        RESP=\$(curl -sf -X POST \
                            -H "Authorization: token \${GITHUB_TOKEN}" \
                            -H "Content-Type: application/json" \
                            "https://api.github.com/repos/${env.REPO}/releases" \
                            --data-binary @/tmp/gh_release_payload.json)
                        NEW_ID=\$(echo "\$RESP" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])" 2>/dev/null)
                        echo "   ✅ Release oluşturuldu (ID: \$NEW_ID)"

                        # Dosyaları yükle
                        UPLOAD_URL="https://uploads.github.com/repos/${env.REPO}/releases/\$NEW_ID/assets"
                        for f in release-artifacts/${env.CORE_RELEASE} release-artifacts/${env.VELOCITY_RELEASE} release-artifacts/${env.API_RELEASE} release-artifacts/SHA256SUMS.txt; do
                            fn=\$(basename "\$f")
                            echo "   📤 Yükleniyor: \$fn"
                            curl -sf -X POST \
                                -H "Authorization: token \${GITHUB_TOKEN}" \
                                -H "Content-Type: application/octet-stream" \
                                "\${UPLOAD_URL}?name=\$fn" \
                                --data-binary "@\$f" > /dev/null && echo "   ✅ \$fn" || echo "   ⚠️  \$fn yüklenemedi"
                        done
                    """
                    echo "✅ GitHub: https://github.com/${env.REPO}/releases/tag/${env.TAG_NAME}"
                }
            }
        }

        // ═════════════════════════════════════════════
        //  9. GITHUB — DEV BUILD (curl/python3)
        // ═════════════════════════════════════════════
        stage('GitHub: Dev Build') {
            when {
                expression { env.RELEASE_TYPE == 'dev' }
            }
            steps {
                script {
                    sh """
                        echo "🔧 GitHub Dev Build: ${env.TAG_NAME}"

                        # Son 5 hariç eski dev build'leri sil
                        curl -sf -H "Authorization: token \${GITHUB_TOKEN}" \
                            "https://api.github.com/repos/${env.REPO}/releases?per_page=50" 2>/dev/null \
                            | python3 -c "
import json, sys, subprocess
releases = json.load(sys.stdin)
dev_releases = [r for r in releases if r.get('tag_name','').startswith('v${env.BASE_VERSION}-dev.')]
for r in dev_releases[5:]:
    rid = r['id']
    tag = r['tag_name']
    print('   Eski dev siliniyor: ' + tag)
    subprocess.run(['curl','-sf','-X','DELETE','-H','Authorization: token \${GITHUB_TOKEN}',
        'https://api.github.com/repos/${env.REPO}/releases/'+str(rid)], capture_output=True)
" 2>/dev/null || true

                        # Mevcut release varsa sil
                        OLD_ID=\$(curl -sf -H "Authorization: token \${GITHUB_TOKEN}" \
                            "https://api.github.com/repos/${env.REPO}/releases/tags/${env.TAG_NAME}" \
                            | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('id',''))" 2>/dev/null || echo "")
                        if [ -n "\$OLD_ID" ] && [ "\$OLD_ID" != "None" ]; then
                            curl -sf -X DELETE -H "Authorization: token \${GITHUB_TOKEN}" \
                                "https://api.github.com/repos/${env.REPO}/releases/\$OLD_ID" || true
                        fi

                        # Release JSON oluştur
                        python3 -c "
import json
notes = open('release-artifacts/RELEASE_NOTES.md').read()
data = {'tag_name':'${env.TAG_NAME}','name':'${env.RELEASE_TITLE}','body':notes,'draft':False,'prerelease':True}
open('/tmp/gh_release_payload.json','w').write(json.dumps(data))
print('Payload hazırlandı')
"
                        RESP=\$(curl -sf -X POST \
                            -H "Authorization: token \${GITHUB_TOKEN}" \
                            -H "Content-Type: application/json" \
                            "https://api.github.com/repos/${env.REPO}/releases" \
                            --data-binary @/tmp/gh_release_payload.json)
                        NEW_ID=\$(echo "\$RESP" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])" 2>/dev/null)
                        echo "   ✅ Dev release oluşturuldu (ID: \$NEW_ID)"

                        # Dosyaları yükle
                        UPLOAD_URL="https://uploads.github.com/repos/${env.REPO}/releases/\$NEW_ID/assets"
                        for f in release-artifacts/${env.CORE_RELEASE} release-artifacts/${env.VELOCITY_RELEASE} release-artifacts/${env.API_RELEASE} release-artifacts/SHA256SUMS.txt; do
                            fn=\$(basename "\$f")
                            echo "   📤 Yükleniyor: \$fn"
                            curl -sf -X POST \
                                -H "Authorization: token \${GITHUB_TOKEN}" \
                                -H "Content-Type: application/octet-stream" \
                                "\${UPLOAD_URL}?name=\$fn" \
                                --data-binary "@\$f" > /dev/null && echo "   ✅ \$fn" || echo "   ⚠️  \$fn yüklenemedi"
                        done
                    """
                    echo "✅ GitHub: https://github.com/${env.REPO}/releases/tag/${env.TAG_NAME}"
                }
            }
        }

        // ═════════════════════════════════════════════
        //  11. MODRINTH — CORE PLUGIN YAYINLA
        // ═════════════════════════════════════════════
        stage('Modrinth: Core Plugin') {
            when {
                expression { env.RELEASE_TYPE != 'none' }
            }
            steps {
                script {
                    def versionType = env.RELEASE_TYPE == 'stable' ? 'release' : 'alpha'
                    def versionName = env.RELEASE_TYPE == 'stable'
                        ? "AtomGuard v${env.BASE_VERSION} (Paper/Spigot)"
                        : "AtomGuard v${env.BASE_VERSION}-dev.${env.COMMIT_COUNT} (Paper/Spigot)"
                    def versionNumber = env.RELEASE_TYPE == 'stable'
                        ? "${env.BASE_VERSION}+core"
                        : "${env.BASE_VERSION}-dev.${env.COMMIT_COUNT}+core"

                    // Changelog'u dosyaya yaz (JSON escape için)
                    writeFile file: 'release-artifacts/modrinth-changelog-core.txt', text: env.MODRINTH_CHANGELOG

                    sh """
                        echo "📦 Modrinth: Core Plugin yayınlanıyor..."

                        # Changelog'u JSON-safe yap
                        CHANGELOG_ESCAPED=\$(python3 -c "
import json, sys
with open('release-artifacts/modrinth-changelog-core.txt', 'r') as f:
    print(json.dumps(f.read()))
" 2>/dev/null || echo '"AtomGuard ${env.RELEASE_VERSION}"')

                        # Modrinth API — Core version oluştur
                        HTTP_CODE=\$(curl -s -o /tmp/modrinth-core-response.json -w "%{http_code}" \\
                            -X POST "https://api.modrinth.com/v2/version" \\
                            -H "Authorization: \${MODRINTH_TOKEN}" \\
                            -F "data={
                                \\"name\\": \\"${versionName}\\",
                                \\"version_number\\": \\"${versionNumber}\\",
                                \\"changelog\\": \${CHANGELOG_ESCAPED},
                                \\"dependencies\\": [
                                    {
                                        \\"project_id\\": \\"hkfCOMjf\\",
                                        \\"dependency_type\\": \\"required\\"
                                    }
                                ],
                                \\"game_versions\\": [\\"1.21.4\\"],
                                \\"version_type\\": \\"${versionType}\\",
                                \\"loaders\\": [\\"paper\\", \\"spigot\\", \\"bukkit\\"],
                                \\"featured\\": ${env.RELEASE_TYPE == 'stable'},
                                \\"project_id\\": \\"\${MODRINTH_ID}\\",
                                \\"file_parts\\": [\\"core-jar\\"],
                                \\"primary_file\\": \\"core-jar\\"
                            };type=application/json" \\
                            -F "core-jar=@release-artifacts/${env.CORE_RELEASE};type=application/java-archive")

                        echo "   HTTP Status: \$HTTP_CODE"

                        if [ "\$HTTP_CODE" -eq 200 ]; then
                            CORE_VERSION_ID=\$(python3 -c "import json; print(json.load(open('/tmp/modrinth-core-response.json'))['id'])" 2>/dev/null || echo "unknown")
                            echo "   ✅ Core yayınlandı! Version ID: \$CORE_VERSION_ID"
                        else
                            echo "   ⚠️ Core yayınlanamadı! Response:"
                            cat /tmp/modrinth-core-response.json
                            echo ""
                        fi
                    """
                }
            }
        }

        // ═════════════════════════════════════════════
        //  12. MODRINTH — VELOCITY PLUGIN YAYINLA
        // ═════════════════════════════════════════════
        stage('Modrinth: Velocity Plugin') {
            when {
                expression { env.RELEASE_TYPE != 'none' }
            }
            steps {
                script {
                    def versionType = env.RELEASE_TYPE == 'stable' ? 'release' : 'alpha'
                    def versionName = env.RELEASE_TYPE == 'stable'
                        ? "AtomGuard v${env.BASE_VERSION} (Velocity)"
                        : "AtomGuard v${env.BASE_VERSION}-dev.${env.COMMIT_COUNT} (Velocity)"
                    def versionNumber = env.RELEASE_TYPE == 'stable'
                        ? "${env.BASE_VERSION}+velocity"
                        : "${env.BASE_VERSION}-dev.${env.COMMIT_COUNT}+velocity"

                    writeFile file: 'release-artifacts/modrinth-changelog-velocity.txt', text: env.MODRINTH_CHANGELOG

                    sh """
                        echo "📦 Modrinth: Velocity Plugin yayınlanıyor..."

                        CHANGELOG_ESCAPED=\$(python3 -c "
import json, sys
with open('release-artifacts/modrinth-changelog-velocity.txt', 'r') as f:
    print(json.dumps(f.read()))
" 2>/dev/null || echo '"AtomGuard Velocity ${env.RELEASE_VERSION}"')

                        # Modrinth API — Velocity version oluştur
                        HTTP_CODE=\$(curl -s -o /tmp/modrinth-velocity-response.json -w "%{http_code}" \\
                            -X POST "https://api.modrinth.com/v2/version" \\
                            -H "Authorization: \${MODRINTH_TOKEN}" \\
                            -F "data={
                                \\"name\\": \\"${versionName}\\",
                                \\"version_number\\": \\"${versionNumber}\\",
                                \\"changelog\\": \${CHANGELOG_ESCAPED},
                                \\"dependencies\\": [],
                                \\"game_versions\\": [\\"1.21.4\\"],
                                \\"version_type\\": \\"${versionType}\\",
                                \\"loaders\\": [\\"velocity\\"],
                                \\"featured\\": false,
                                \\"project_id\\": \\"\${MODRINTH_ID}\\",
                                \\"file_parts\\": [\\"velocity-jar\\"],
                                \\"primary_file\\": \\"velocity-jar\\"
                            };type=application/json" \\
                            -F "velocity-jar=@release-artifacts/${env.VELOCITY_RELEASE};type=application/java-archive")

                        echo "   HTTP Status: \$HTTP_CODE"

                        if [ "\$HTTP_CODE" -eq 200 ]; then
                            VEL_VERSION_ID=\$(python3 -c "import json; print(json.load(open('/tmp/modrinth-velocity-response.json'))['id'])" 2>/dev/null || echo "unknown")
                            echo "   ✅ Velocity yayınlandı! Version ID: \$VEL_VERSION_ID"
                        else
                            echo "   ⚠️ Velocity yayınlanamadı! Response:"
                            cat /tmp/modrinth-velocity-response.json
                            echo ""
                        fi
                    """
                }
            }
        }
    }

    // ═════════════════════════════════════════════
    //  POST ACTIONS
    // ═════════════════════════════════════════════
    post {
        success {
            script {
                def emoji = env.RELEASE_TYPE == 'stable' ? '🚀' : (env.RELEASE_TYPE == 'dev' ? '🔧' : '✅')
                def ghUrl = env.TAG_NAME ? "https://github.com/${env.REPO}/releases/tag/${env.TAG_NAME}" : 'N/A'
                def mrUrl = (env.RELEASE_TYPE != 'none') ? "https://modrinth.com/plugin/atomguard/versions" : 'N/A'
                echo """
                ╔═══════════════════════════════════════════════════╗
                ║  ${emoji} AtomGuard BUILD SUCCESS
                ╠═══════════════════════════════════════════════════╣
                ║  Version  : ${env.RELEASE_VERSION}
                ║  Type     : ${env.RELEASE_TYPE}
                ║  Commit   : ${env.GIT_COMMIT_SHORT} — ${env.GIT_COMMIT_MSG}
                ║  GitHub   : ${ghUrl}
                ║  Modrinth : ${mrUrl}
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
            deleteDir()
        }
    }
}
