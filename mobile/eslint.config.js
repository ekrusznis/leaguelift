// https://docs.expo.dev/guides/using-eslint/
const { defineConfig } = require('eslint/config');
const expoConfig = require("eslint-config-expo/flat");

module.exports = defineConfig([
  expoConfig,
  {
    ignores: ["dist/*"],
  },
  {
    // Without this, eslint-plugin-import doesn't know about Metro's .ios.tsx/
    // .android.tsx platform-extension resolution and false-positives on every
    // import of a platform-split module (see tsconfig.json's moduleSuffixes,
    // which already teaches tsc the same convention).
    settings: {
      "import/resolver": {
        typescript: {
          extensions: [".ios.tsx", ".android.tsx", ".native.tsx", ".tsx", ".ts", ".js", ".jsx"],
        },
      },
    },
  },
]);
