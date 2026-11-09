# One entrypoint, so humans and agents run the same commands.

PRETTIER ?= npx --yes --prefer-offline prettier@latest

# Gradle needs a JDK. A Mac with only Android Studio has one inside the app.
STUDIO_JBR := /Applications/Android Studio.app/Contents/jbr/Contents/Home
export JAVA_HOME ?= $(shell [ -d "$(STUDIO_JBR)" ] && echo "$(STUDIO_JBR)")
GRADLE = ./gradlew -q

.PHONY: help fmt lint test check debug install release archive

help:
	@echo 'fmt      rewrite files: ktlint on Kotlin, prettier on Markdown -- the only target that edits anything'
	@echo 'lint     ktlint, Android lint, prettier --check'
	@echo 'test     the JVM unit tests (the rotation rules)'
	@echo 'check    lint test'
	@echo 'debug    build the debug APK (io.github.vivekg7.badal.debug, beside a release install)'
	@echo 'install  build the debug APK and install it on ANDROID_SERIAL'
	@echo 'release  build the minified release APK and Play bundle (AAB), signed when local/keystore.properties exists'
	@echo 'archive  build, verify and keep the signed release APK and AAB in local/releases/'

fmt:
	$(GRADLE) ktlintFormat
	$(PRETTIER) --write '**/*.md'

lint:
	$(GRADLE) ktlintCheck :app:lintDebug
	$(PRETTIER) --check '**/*.md'

test:
	$(GRADLE) :app:testDebugUnitTest

check: lint test

debug:
	$(GRADLE) :app:assembleDebug

# installDebug installs on every connected device: name the one you mean.
install:
	@[ -n "$$ANDROID_SERIAL" ] || { echo 'set ANDROID_SERIAL, e.g. emulator-5554 (adb devices lists them)'; exit 1; }
	$(GRADLE) :app:installDebug

release:
	$(GRADLE) :app:assembleRelease :app:bundleRelease
	@ls -l app/build/outputs/apk/release/ app/build/outputs/bundle/release/

archive:
	./scripts/archive-apk.sh
