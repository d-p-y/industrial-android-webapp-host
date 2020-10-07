# Privacy Policy

This document contains privacy policy for `Industrial Android Web Application Host` (shortly: `IAWApp Host`, simply called `App` later in text). It is written due to GDPR (General Data Protection Regulation) obligatory in European Union.

# Identity

App is developed by Dominik Pytlewski, an individual from Warsaw, Poland, EU. App is my [open source project](https://github.com/d-p-y/industrial-android-webapp-host).

# Personal Data processing

App basically is an [Android WebView](https://developer.android.com/reference/android/webkit/WebView) based web browser providing additional APIs. Those APIs let software developers build websites that act within App as if they were native Android applications. App contains Connection Manager (URL list management screens). App expects its user to register URLs himself/herself by means of Connection Manager. There is no fixed list of URLs that can be utilized by App. App doesn't maintain or moderate URLs registered into Connection Manager. By default APIs are not accessible but user may decide to permit access to those APIs to URLs that he/she deems as trustworthy. As of 2020-07 those sensitive APIs are: 
- photo taking 
- QR scanning by means of phone camera

Both features in App are meant to keep data locally on Android device and not share it with other applications or any other external computer. Photo taking API stores taken photos into Android App private cache directory. Images in cache are not accessible for other apps installed on Android device. Android automatically deletes files in cache directories when it is running out of space.

 QR scanning is built around [Google ZXing open source library](https://zxing.github.io/zxing/) that according to my knowledge does processing locally by means of image processing on android device. 

Unhandled exceptions (software developer oriented information about errors causing app crashes) are stored locally within data folder of the App. It is not accessible to other Android apps. It is not transferred outside of device. If somebody wants to use it to troubleshoot App OR ask me for help he/she may use it to make it easier to pinpoint bug BUT he/she needs to have technical skill to manually take it out of Android device.

Take note that App is de facto a web browser. Opening URL in App causes website to be fetched from network and javascript to be executed. This means that website may access features such as Cookies and Local Storage that can be used to store Personal Data. I don't control what websites you use with App. Refer to specific website operators to know how they process your Personal Data.

Website may alter fragment part of its URL (data after 'hash character') to store information. It is intended to let websites answer questions such as: what was the app state before it was minimized. URL together with its fragment part is stored in connection manager in file stored in app Android data directory.

I don't have access (and don't have any intent to gain access) to taken photos, scanned QR codes or any other data processed by App. 

Also take note that App is a regular Android application. If you decide to use "App backup" feature of Android you will potentially cause private data directory to be shared with Google. Don't use Android "App backup" feature if you don't want this data to leave the device.

# Contact

Dominik Pytlewski 
ind.and.web.app@gmail.com

# History

2020-07-28 created this document
