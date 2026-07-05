# Pull Request: fix/retry-backoff-and-safety

This PR contains reliability and safety improvements for ADB operations, plus documentation of removable OEM packages.

What changed

- Add retry with exponential backoff for transient failures (AdbRepository).
- Improve stream/resource handling (use/try/finally) to avoid leaks.
- Add logging via Timber and initialize it in Application.
- Make installApk use retry logic and safer cleanup.
- Add docs/removable_packages.md with OPPO/realme and Xiaomi package lists you provided.
- Add Timber dependency to app/build.gradle.kts.

Testing notes

- Build locally in Android Studio and test pairing + install flows on a spare device.
- Actions CI not yet run for this branch — please open PR to trigger.

Risks

- Adding Timber dependency and planting DebugTree in Application will log to Logcat; consider controlling this in release builds.
- Removing system packages listed in docs is risky; test on a non-critical device.

Next steps

- I can open a PR on your behalf (requires repository permissions not available from this tool). Alternatively, you can open a PR from branch fix/retry-backoff-and-safety.
- I can add ktlint/detekt to CI in a follow-up PR.

How to open PR locally (GitHub UI)

1. Go to: https://github.com/artemmetra2022/APK
2. You should see a banner "Compare & pull request" for branch fix/retry-backoff-and-safety — click it.
3. Add description and create PR.

If you want, I can continue and add CI checks and further refactoring in subsequent commits.
