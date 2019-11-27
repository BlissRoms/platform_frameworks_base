# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := frameworks-base-overlays
LOCAL_REQUIRED_MODULES := \
	AccentColorBlackOverlay \
	AccentColorCinnamonOverlay \
	AccentColorOceanOverlay \
	AccentColorOrchidOverlay \
	AccentColorSpaceOverlay \
	AccentColorGreenOverlay \
	AccentColorPurpleOverlay \
	DisplayCutoutEmulationCornerOverlay \
	DisplayCutoutEmulationDoubleOverlay \
	DisplayCutoutEmulationTallOverlay \
	FontArbutusSourceOverlay \
	FontArvoLatoOverlay \
	FontGoogleSansOverlay \
	FontNotoSerifSourceOverlay \
	FontRubikRubikOverlay \
	FontAlpacaScarlettOverlay \
	FontAlpacaSolidifyOverlay \
	FontASansroundedOverlay
	FontAmaranteOverlay
	FontAnaheimRegularOverlay
	FontAnitaOverlay
	FontArkitechOverlay
	FontAsapOverlay
	FontAssassinOverlay
	FontAtmaOverlay
	FontAudiowideOverlay
	FontBariolLightOverlay
	FontBariolRegularOverlay
	FontBarlowOverlay
	FontBauhausOverlay
	FontCagliostroLightOverlay
	FontCagliostroRegularOverlay
	FontCalibriRegularOverlay
	FontCaviarDreamsOverlay
	FontCaviarDreamsBoldOverlay
	FontCaviarDreamsBoldItalicOverlay
	FontCaviarDreamsItalicOverlay
	FontChococookyOverlay
	FontClearSansOverlay
	FontCoconThinOverlay
	FontComfortaaRegularOverlay
	FontComicNeueSansIDOverlay
	FontComicSansOverlay
	FontCooljazzOverlay
	FontDigitalOverlay
	FontDosisRegularOverlay
	FontFaerytale_WoodsOverlay
	FontFifa2018Overlay
	FontFilthOfIcarusOverlay
	FontFoxAndCatOverlay
	FontGafataOverlay
	FontGinoraSansOverlay
	FontGoogleSansRegularOverlay
	FontHYCoffeeOverlay
	FontHelveticaNeueOverlay
	FontInkfernoOverlay
	FontKGMissKindergartenOverlay
	FontLGSmartGothicOverlay
	FontLiberation_SerifOverlay
	FontLifeIsABeachOverlay
	FontLovelynOverlay
	FontMalgunGothicOverlay
	FontModernSerifOverlay
	FontMontserratAlternatesOverlay
	FontMuseo300Overlay
	FontNK57Overlay
	FontNoirOverlay
	FontNokiaPureOverlay
	FontNoteworthyOverlay
	FontNunitoRegularOverlay
	FontOHD5Overlay
	FontOpenSansLightOverlay
	FontOswaldOverlay
	FontPoppinsOverlay
	FontProductSansOverlay
	FontQontraOverlay
	FontQuickOverlay
	FontRalewayOverlay
	FontRosemaryRegularOverlay
	FontRosemaryRomanOverlay
	FontRoundedEleganceOverlay
	FontRubikOverlay
	FontSAORegularOverlay
	FontSamarkanOverlay
	FontSamsungOneOverlay
	FontSamsungregulerOverlay
	FontSanFranciscoProOverlay
	FontSlateFromOPLightOverlay
	FontSlateFromOPRegularOverlay
	FontSonySketchOverlay
	FontSquaredOverlay
	FontStoropiaOverlay
	FontTimelessOverlay
	FontTitilliumWebOverlay
	FontTransformersOverlay
	FontTypographyOverlay
	FontUbuntuTitleOverlay
	FontUbuntuRegularOverlay
	FontUnicornOverlay
	FontValentineLoveOverlay
	FontVocesLightOverlay
	FontVocesRegularOverlay
	FontWalkwayOverlay
	FontXperiaOverlay
	FontbansheeOverlay
	FontbatmanOverlay
	FontbigriverOverlay
	FontbigriverscriptOverlay
	FontchinrgOverlay
	FontchintOverlay
	FontcoolstoryregularOverlay
	FontfifawelcomeOverlay
	FontkenyancoffeergOverlay
	FontloveOverlay
	Fontsamsung_boldOverlay
	FontscrewdOverlay
	FontubuntutitleOverlay
	IconPackCircularAndroidOverlay \
	IconPackCircularLauncherOverlay \
	IconPackCircularSettingsOverlay \
	IconPackCircularSystemUIOverlay \
	IconPackCircularThemePickerOverlay \
	IconPackFilledAndroidOverlay \
	IconPackFilledLauncherOverlay \
	IconPackFilledSettingsOverlay \
	IconPackFilledSystemUIOverlay \
	IconPackFilledThemePickerOverlay \
	IconPackRoundedAndroidOverlay \
	IconPackRoundedLauncherOverlay \
	IconPackRoundedSettingsOverlay \
	IconPackRoundedSystemUIOverlay \
	IconPackRoundedThemePickerOverlay \
	IconShapeRoundedRectOverlay \
	IconShapeSquircleOverlay \
	IconShapeTeardropOverlay \
	NavigationBarMode3ButtonOverlay \
	NavigationBarMode2ButtonOverlay \
	NavigationBarModeGesturalOverlay \
	NavigationBarModeGesturalOverlayNarrowBack \
	NavigationBarModeGesturalOverlayWideBack \
	NavigationBarModeGesturalOverlayExtraWideBack

include $(BUILD_PHONY_PACKAGE)
include $(CLEAR_VARS)

LOCAL_MODULE := frameworks-base-overlays-debug

include $(BUILD_PHONY_PACKAGE)
include $(call first-makefiles-under,$(LOCAL_PATH))
