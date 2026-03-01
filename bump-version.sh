#!/bin/bash
#
# AtomGuard — Sürüm Yükseltme Aracı
# Kullanım: ./bump-version.sh <major|minor|patch|X.Y.Z> [--tag] [--push]
#
# Örnekler:
#   ./bump-version.sh patch           → 1.2.2 → 1.2.3
#   ./bump-version.sh minor           → 1.2.2 → 1.3.0
#   ./bump-version.sh major           → 1.2.2 → 2.0.0
#   ./bump-version.sh 2.0.0           → direkt sürüm belirle
#   ./bump-version.sh patch --tag     → versiyon yükselt + git tag oluştur
#   ./bump-version.sh patch --tag --push → yukarıdaki + push et

set -euo pipefail

# ─── Renkler ───
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# ─── Argümanlar ───
BUMP_TYPE="${1:-}"
DO_TAG=false
DO_PUSH=false

for arg in "$@"; do
    case "$arg" in
        --tag)  DO_TAG=true ;;
        --push) DO_PUSH=true ;;
    esac
done

if [ -z "$BUMP_TYPE" ]; then
    echo -e "${RED}Kullanım: $0 <major|minor|patch|X.Y.Z> [--tag] [--push]${NC}"
    echo ""
    echo "  major    → X+1.0.0"
    echo "  minor    → X.Y+1.0"
    echo "  patch    → X.Y.Z+1"
    echo "  X.Y.Z    → Belirtilen sürümü ayarla"
    echo ""
    echo "  --tag    → Git tag oluştur (vX.Y.Z)"
    echo "  --push   → Commit ve tag'ı push et"
    exit 1
fi

# ─── Mevcut sürümü al ───
CURRENT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null)
if [ -z "$CURRENT_VERSION" ]; then
    echo -e "${RED}❌ pom.xml'den sürüm okunamadı!${NC}"
    exit 1
fi

IFS='.' read -r CUR_MAJOR CUR_MINOR CUR_PATCH <<< "$CURRENT_VERSION"

echo -e "${CYAN}╔══════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║     AtomGuard Sürüm Yükseltme Aracı     ║${NC}"
echo -e "${CYAN}╠══════════════════════════════════════════╣${NC}"
echo -e "${CYAN}║  Mevcut sürüm: ${BOLD}${CURRENT_VERSION}${NC}${CYAN}                      ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════╝${NC}"

# ─── Yeni sürümü hesapla ───
case "$BUMP_TYPE" in
    major)
        NEW_MAJOR=$((CUR_MAJOR + 1))
        NEW_MINOR=0
        NEW_PATCH=0
        ;;
    minor)
        NEW_MAJOR=$CUR_MAJOR
        NEW_MINOR=$((CUR_MINOR + 1))
        NEW_PATCH=0
        ;;
    patch)
        NEW_MAJOR=$CUR_MAJOR
        NEW_MINOR=$CUR_MINOR
        NEW_PATCH=$((CUR_PATCH + 1))
        ;;
    *)
        # Direkt sürüm numarası (X.Y.Z formatı)
        if [[ "$BUMP_TYPE" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
            IFS='.' read -r NEW_MAJOR NEW_MINOR NEW_PATCH <<< "$BUMP_TYPE"
        else
            echo -e "${RED}❌ Geçersiz argüman: $BUMP_TYPE${NC}"
            echo "   Kullanım: major | minor | patch | X.Y.Z"
            exit 1
        fi
        ;;
esac

NEW_VERSION="${NEW_MAJOR}.${NEW_MINOR}.${NEW_PATCH}"

if [ "$NEW_VERSION" == "$CURRENT_VERSION" ]; then
    echo -e "${YELLOW}⚠️  Sürüm zaten $CURRENT_VERSION, değişiklik yok.${NC}"
    exit 0
fi

echo -e "${GREEN}📦 Yeni sürüm: ${BOLD}${NEW_VERSION}${NC}"
echo ""

# ─── 1. Maven pom.xml (tüm modüller) ───
echo -e "${YELLOW}[1/5] Maven pom.xml güncelleniyor...${NC}"
mvn versions:set -DnewVersion="$NEW_VERSION" -DgenerateBackupPoms=false -q
echo -e "      ${GREEN}✅ pom.xml → ${NEW_VERSION}${NC}"

# ─── 2. BuildInfo.java (Core) ───
BUILDINFO="core/src/main/java/com/atomguard/BuildInfo.java"
if [ -f "$BUILDINFO" ]; then
    echo -e "${YELLOW}[2/5] BuildInfo.java güncelleniyor...${NC}"
    sed -i "s/VERSION_MAJOR = [0-9]*/VERSION_MAJOR = ${NEW_MAJOR}/" "$BUILDINFO"
    sed -i "s/VERSION_MINOR = [0-9]*/VERSION_MINOR = ${NEW_MINOR}/" "$BUILDINFO"
    sed -i "s/VERSION_PATCH = [0-9]*/VERSION_PATCH = ${NEW_PATCH}/" "$BUILDINFO"
    echo -e "      ${GREEN}✅ BuildInfo.java → ${NEW_VERSION}${NC}"
else
    echo -e "      ${YELLOW}⚠️  BuildInfo.java bulunamadı, atlanıyor.${NC}"
fi

# ─── 3. VelocityBuildInfo.java ───
VBUILDINFO="velocity/src/main/java/com/atomguard/velocity/VelocityBuildInfo.java"
if [ -f "$VBUILDINFO" ]; then
    echo -e "${YELLOW}[3/5] VelocityBuildInfo.java güncelleniyor...${NC}"
    sed -i "s/VERSION = \"[0-9]*\.[0-9]*\.[0-9]*\"/VERSION = \"${NEW_VERSION}\"/" "$VBUILDINFO"

    # BUILD_DATE'i de güncelle
    TODAY=$(date +%Y-%m-%d)
    sed -i "s/BUILD_DATE = \"[0-9-]*\"/BUILD_DATE = \"${TODAY}\"/" "$VBUILDINFO"
    echo -e "      ${GREEN}✅ VelocityBuildInfo.java → ${NEW_VERSION} (${TODAY})${NC}"
else
    echo -e "      ${YELLOW}⚠️  VelocityBuildInfo.java bulunamadı, atlanıyor.${NC}"
fi

# ─── 4. ConfigManager currentVersion (varsa) ───
CONFIG_MANAGER=$(find . -name "ConfigManager.java" -path "*/atomguard/*" | head -1)
if [ -n "$CONFIG_MANAGER" ]; then
    echo -e "${YELLOW}[4/5] ConfigManager güncelleniyor...${NC}"
    if grep -q 'currentVersion = "' "$CONFIG_MANAGER"; then
        sed -i "s/currentVersion = \"[0-9]*\.[0-9]*\.[0-9]*\"/currentVersion = \"${NEW_VERSION}\"/" "$CONFIG_MANAGER"
        echo -e "      ${GREEN}✅ ConfigManager → ${NEW_VERSION}${NC}"
    else
        echo -e "      ${YELLOW}⚠️  currentVersion bulunamadı, atlanıyor.${NC}"
    fi
else
    echo -e "      ${YELLOW}⚠️  ConfigManager.java bulunamadı, atlanıyor.${NC}"
fi

# ─── 5. CHANGELOG.md'ye yeni bölüm ekle ───
echo -e "${YELLOW}[5/5] CHANGELOG.md güncelleniyor...${NC}"
TODAY=$(date +%Y-%m-%d)
if [ -f "CHANGELOG.md" ]; then
    # Zaten bu sürüm var mı kontrol et
    if grep -q "\[${NEW_VERSION}\]" CHANGELOG.md; then
        echo -e "      ${YELLOW}⚠️  [${NEW_VERSION}] zaten CHANGELOG.md'de mevcut, atlanıyor.${NC}"
    else
        # Yalnızca ilk ## [ satırından önce yeni bölüm ekle (0,/pattern/ ile tek eşleşme)
        sed -i "0,/^## \[/{/^## \[/i\\
## [${NEW_VERSION}] - ${TODAY}\\
\\
### ✨ Yeni Özellikler\\
\\
- \\
\\
### 🔧 İyileştirmeler\\
\\
- \\
\\
### 🐛 Hata Düzeltmeleri\\
\\
- \\

}" CHANGELOG.md
        echo -e "      ${GREEN}✅ CHANGELOG.md → [${NEW_VERSION}] bölümü eklendi${NC}"
    fi
else
    echo -e "      ${YELLOW}⚠️  CHANGELOG.md bulunamadı, atlanıyor.${NC}"
fi

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  ✅ Sürüm güncellendi: ${BOLD}${CURRENT_VERSION} → ${NEW_VERSION}${NC}${GREEN}  ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
echo ""

# ─── Git Tag ───
if [ "$DO_TAG" = true ]; then
    echo -e "${YELLOW}🏷️  Git commit + tag oluşturuluyor...${NC}"

    git add -A
    git commit -m "🔖 Release v${NEW_VERSION}

- pom.xml sürüm güncellendi
- BuildInfo.java sürüm güncellendi
- VelocityBuildInfo.java sürüm güncellendi
- CHANGELOG.md bölümü eklendi"

    git tag -a "v${NEW_VERSION}" -m "AtomGuard v${NEW_VERSION}"

    echo -e "${GREEN}✅ Commit ve tag oluşturuldu: v${NEW_VERSION}${NC}"

    if [ "$DO_PUSH" = true ]; then
        echo -e "${YELLOW}📤 Push ediliyor...${NC}"
        git push origin HEAD
        git push origin "v${NEW_VERSION}"
        echo -e "${GREEN}✅ Push tamamlandı! Jenkins otomatik tetiklenecek.${NC}"
    else
        echo ""
        echo -e "${CYAN}Push etmek için:${NC}"
        echo "  git push origin HEAD"
        echo "  git push origin v${NEW_VERSION}"
    fi
else
    echo -e "${CYAN}Sonraki adımlar:${NC}"
    echo "  1. CHANGELOG.md'yi düzenle (değişiklikleri yaz)"
    echo "  2. git add -A && git commit -m '🔖 Release v${NEW_VERSION}'"
    echo "  3. git tag -a v${NEW_VERSION} -m 'AtomGuard v${NEW_VERSION}'"
    echo "  4. git push origin HEAD && git push origin v${NEW_VERSION}"
fi
