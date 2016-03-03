# Gesture Unlock with Custom Shortcuts
![Gesture Unlock (Logo)](https://raw.githubusercontent.com/Rijul-Ahuja/GestureUnlock/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

This Xposed module enables gesture unlock for Lollipop and Marshmallow devices . The gesture unlock is highly customizable, from changing colours to visibility, error messages, background, etc. You can even hide the emergency button.

What's more, you can use gestures to directly open specific shortcuts from the lockscreen, for example use U to unlock, W to open WhatsApp, M for email, etc. These shortcuts don't have to be just apps, they can be anything on your device, like Direct Dial, open a specific Contact, etc.

The module is pretty self explanatory, and will prompt you to set a pattern on the lock screen, because that is what it replaces. Other than that, there are no specific instructions to use it. Should the module or Xposed be disabled for any reason, your phone will still remain secure with that pattern.

Compatibility :
I personally test on CM13, and I will support CM12.0, CM12.1, AOSP 5.x and 6.0.x and derivatives. HTC support is limited unless I find a tester. Support for other OEM ROMs is absent beyond basic working functionality.

The only caveats are because of the way Xposed works.
A. Your gestures will be visible to any one or any app on your device. No root required.
B. You need root to restart the keyguard after changing the gesture full screen option. It is not mandatory, you could manually reboot if you require. All other changes will be reflected automatically, but not this one.

Links:
------
+ [XDA Forum](http://forum.xda-developers.com/xposed/modules/aosp-cm-htc-gesture-unlock-custom-t3328257)
+ [Xposed Repo](http://repo.xposed.info/module/me.rijul.gestureunlock)

Thanks to:
------
+ [ColorPickerPreference](https://github.com/attenzione/android-ColorPickerPreference)
+ [GravityBox's SeekBarPreference](https://github.com/GravityBox/GravityBox/blob/marshmallow/src/com/ceco/marshmallow/gravitybox/preference/SeekBarPreference.java)
+ [Temasek's ShortcutPickHelper](https://github.com/temasek/android_packages_apps_Settings/blob/cm-13.0/src/com/android/settings/cyanogenmod/ShortcutPickHelper.java)
+ [Temasek's GestureOverlayView](https://github.com/temasek/android_frameworks_base/blob/cm-12.1/core/java/android/gesture/GestureOverlayView.java)
+ [Temasek's GestureAnywhere](https://github.com/temasek/android_packages_apps_Settings/tree/cm-13.0/src/com/android/settings/temasek/gestureanywhere)
+ [Temasek's GestureLockScreen](https://github.com/temasek/android_frameworks_base/commit/d89f57baa936d6c2114e1a3726ca4f4f8f2b2437)
+ [Tyaginator@XDA for the custom shortcuts idea](http://forum.xda-developers.com/member.php?u=5327227)
+ [PIN/Pattern Shortcuts](http://repo.xposed.info/module/com.hamzah.pinshortcuts)
+ [MohammadAG's KnockCode for the inspiration](http://repo.xposed.info/module/com.mohammadag.knockcode)
