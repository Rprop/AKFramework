# Warning
***Install framework at your own risk. It may damage your device and avoid the warranty.***   
*If the system failed to boot within 160 seconds, the framework will disable itself.*

# Usage
adb push AKCore.apk /data/local/tmp/   
adb push libAK.so /data/local/tmp/   
adb push android_runtime.so /data/local/tmp/   
adb push ak_installer /data/local/tmp/   
adb root
adb shell chmod 555 /data/local/tmp/ak_installer

# Preverify
./ak_installer preverify

# Install
./ak_installer install

# Uninstall
./ak_installer uninstall

# Reinstall
./ak_installer reinstall

# Screenshots
After successful installation (***zygote restart required***), xposed installer and most of the xposed modules should be working properly.   
![Xposed](https://github.com/Rprop/AKFramework/raw/master/screenshot.png)
