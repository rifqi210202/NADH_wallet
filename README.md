<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/70b139d3-b4c4-4e81-baa5-bd68b155cf87

## Run Locally

**Prerequisites:**  Node.js


1. Install dependencies:
   `npm install`
2. Set the `GEMINI_API_KEY` in [.env.local](.env.local) to your Gemini API key
3. Run the app:
   `npm run dev`

## Run Website + NADH Wallet API

Untuk membuat tagihan checkout muncul di APK Android, jalankan frontend dan API lokal bersamaan:

```bash
npm run dev:all
```

Frontend berjalan di `http://IP-LAPTOP:3000`, sedangkan API berjalan di port `4000` dan diproxy oleh Vite lewat `/api`. Di APK, isi alamat website dengan:

```text
http://IP-LAPTOP:3000
```

Selama HP/emulator berada di WiFi yang sama dan firewall mengizinkan port `3000`, APK akan login ke endpoint `POST /api/wallet/auth/login`, membaca tagihan dari `GET /api/wallet/payment-requests`, dan membayar lewat `POST /api/wallet/payment-requests/{id}/pay`.

Login demo:

```text
Email: rifqinadhiraltaz25@gmail.com
Password: password123
PIN bayar: 123456
```

Di website, buka layar tagihan lalu klik `Simulasikan Checkout NADH_Wallet` untuk membuat tagihan baru yang akan muncul di APK setelah refresh layar Tagihan.
