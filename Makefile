KTLINT_VERSION := 1.5.0
KTLINT         := ./ktlint

.PHONY: setup format lint test clean xray-android xray-ios xray

setup:
	curl -sSLO https://github.com/pinterest/ktlint/releases/download/$(KTLINT_VERSION)/ktlint
	chmod +x ktlint

format: setup
	$(KTLINT) --format "{shared,androidApp}/src/**/*.kt"

lint: setup
	$(KTLINT) --relative "{shared,androidApp}/src/**/*.kt"

test:
	./gradlew :shared:domain:jvmTest :shared:data:jvmTest

clean:
	./gradlew clean
	rm -f ktlint

xray-android:
	bash xray-build/build-android.sh
	mkdir -p androidApp/libs
	cp xray-build/output/android/XrayCore.aar androidApp/libs/

xray-ios:
	bash xray-build/build-ios.sh
	mkdir -p iosApp/Frameworks
	cp -R xray-build/output/ios/LibXray.xcframework iosApp/Frameworks/

xray:
	bash xray-build/build.sh
