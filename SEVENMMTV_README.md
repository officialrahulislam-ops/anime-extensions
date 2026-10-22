# Personal 7MMTV extension (Zangetsu / Yuzono-compatible)

Personal use only. Status: written against saved page sources; **not yet compiled or run** (see "Untested").

## One-time setup (about 10 minutes, browser only)
1. Sign in to GitHub. Open https://github.com/yuzono/anime-extensions and click **Fork** (keep the default settings, create the fork).
2. In YOUR fork click **Add file > Upload files**. Drag in the contents of this folder (the `src` and `.github` folders). Click **Commit changes**.
3. In your fork: **Settings > Actions > General > Workflow permissions** > choose **Read and write permissions** > Save.
4. Open the **Actions** tab. If asked, click "I understand my workflows, go ahead and enable them".
5. Click **Personal 7MMTV build and publish** (left side) > **Run workflow** > Run. Wait for the green tick (the first build can take 10-20 minutes).
6. Your repository URL is:
   `https://raw.githubusercontent.com/<YOUR_GITHUB_USERNAME>/anime-extensions/repo/index.min.json`
   Open it in a browser: you should see JSON text. (The run log prints this URL on its last step.)
7. In Zangetsu add that URL as an extension repository, then install **7MMTV**.

## Updating
Edit files under `src/en/sevenmmtv/` on GitHub, **increase `extVersionCode` in `build.gradle` by 1**, commit. The build runs by itself and republishes.

## If a build fails
Open the failed run in the Actions tab, copy the red error lines and send them to Claude.

## Notes
- The fork is public (forks of public repos cannot be private), so the index URL is reachable by anyone who knows it.
- `.github/keystore/personal-debug.keystore` is a throwaway signing key so updates install over older builds. Do not reuse it for anything else.
- No secrets are needed; the workflow uses GitHub's built-in token.

## Untested (be aware)
- Compilation, the APK, the index format against Zangetsu, search results, SW and SP servers, and playback in Zangetsu.
