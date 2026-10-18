#!/usr/bin/env sh
set -e

pnpm exec husky init
mkdir -p .husky
cat > .husky/pre-commit << 'EOF'
#!/usr/bin/env sh
pnpm lint-staged
EOF
chmod +x .husky/pre-commit
