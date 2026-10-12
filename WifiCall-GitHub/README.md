# Local Wi-Fi Calling

একই Wi-Fi / Hotspot নেটওয়ার্কের মধ্যে ইন্টারনেট ছাড়া একজন ব্যবহারকারী (Caller) অন্যজনকে (Receiver) তাৎক্ষণিক রিং দিতে পারে।

- ২০ জন Caller, ৫টি Receiver স্লট
- Receiver ফোনে জোরে রিং (STREAM_ALARM), লক স্ক্রিনেও দেখায়, Stop চাপলে বন্ধ
- Receiver স্লটের কাস্টম নাম, Caller নাম পরিবর্তন, DND access অপশন

## APK বানানোর উপায়

**GitHub Actions দিয়ে (কম্পিউটার ছাড়াই):**
1. এই রিপোজিটরিতে ফাইলগুলো push/upload করো
2. **Actions** ট্যাব → **Build APK** → রান শেষ হলে নিচে **Artifacts** থেকে `WifiCall-debug-apk` ডাউনলোড করো
3. zip খুলে `app-debug.apk` ফোনে ইনস্টল করো

**Android Studio দিয়ে:** প্রজেক্ট Open করে Build → Build APK(s)।

## ব্যবহার
সব ফোনে একই apk ইনস্টল করো, একই Wi-Fi-তে কানেক্ট রাখো। Receiver ফোনে স্লট বেছে "চালু করুন" চাপো, ব্যাটারি অপটিমাইজেশন বন্ধ রাখো এবং DND access দাও।
