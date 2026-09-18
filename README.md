# CasaZapp TV – Android

Android-apps voor [CasaZapp TV](https://github.com/QuadNL/CasaZapp-tv). Eerst de **Android TV-app**; een telefoon/tablet-app kan later dezelfde basis gebruiken.

Het plan en de keuzes staan in de hoofdrepo: [`docs/plans/android-tv.md`](https://github.com/QuadNL/CasaZapp-tv/blob/main/docs/plans/android-tv.md).

## Modules

| Module  | Inhoud                                                                          |
| ------- | ------------------------------------------------------------------------------- |
| `:core` | Alles zonder UI: API-client voor de CasaZapp TV-server, koppelen, opslag         |
| `:tv`   | De Android TV-app: Compose met `tv-material`, bediening met de afstandsbediening |

Later komt `:mobile` erbij als een telefoon-app naast de PWA nodig blijkt.

## Wat werkt (0.1.0)

- **Koppelen met je server**: de tv toont een code, jij keurt die goed in de webapp onder **Instellingen → Apparaten**. Geen wachtwoord typen op de tv.
- **Zenderlijst**: dezelfde selectie als in de webapp, met je eigen lijsten als filter, logo's en wat er nu speelt.
- **Kijken**: de tv speelt **rechtstreeks bij de provider** (Media3/ExoPlayer, HLS en MPEG-TS). Er loopt geen videoverkeer via je server.
- **Zappen**: pijl omhoog/omlaag of de kanaaltoetsen; OK toont de zenderinformatie; Terug gaat naar de lijst.

Nog niet: lokale modus (zonder server), gids-raster, nummer intoetsen, update-check.

## Bouwen

1. Installeer [Android Studio](https://developer.android.com/studio) en open deze map.
2. Android Studio maakt zelf `local.properties` aan met het pad naar de SDK.
3. Kies de configuratie **tv** en een tv-emulator (_Device Manager → Television 1080p_) of je echte tv.

Vanaf de command line (Windows, met de JDK van Android Studio):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :tv:assembleDebug
```

De APK staat daarna in `tv/build/outputs/apk/debug/tv-debug.apk`.

## Op je tv zetten

1. Op de tv: _Instellingen → Apparaatvoorkeuren → Over → Build_ zeven keer aanklikken. Dan onder _Ontwikkelaarsopties_ **USB-foutopsporing** (en bij Google TV **Draadloze foutopsporing**) aanzetten.
2. Op je pc, in hetzelfde netwerk:

   ```bash
   adb connect <ip-van-je-tv>:5555
   adb install -r tv/build/outputs/apk/debug/tv-debug.apk
   ```

3. Open **CasaZapp TV** op de tv, vul het adres van je server in (bijv. `https://casazapp.jouwdomein.nl`) en kies **Koppelcode aanvragen**.
4. Open de webapp op je telefoon of pc → **Instellingen → Apparaten**, vul de code in en kies **Koppelen**. De tv gaat vanzelf verder.

## Werkafspraken

Zelfde als de hoofdrepo: code, commentaar en commits in het Engels, documentatie en issues in het Nederlands, de app zelf in NL en EN.
