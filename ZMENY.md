# PlayCaption – co je v které verzi

Číslo verze se ukazuje v aplikaci vpravo nahoře (např. BETA 0.10) a v Záznamu (logu).
Od verze 0.10 se vyvíjí jen jedna aplikace (dřívější NextGen, větev `master`) – umí „Jen televize“ i „S telefonem“.
Při každé nové verzi se zvýší číslo v `app/build.gradle.kts` (versionCode + versionName) a sem se dopíše, co je nového.

## 0.20.1-fuki21
- Titulky neskáčou: když běží AI, neukazuje se rychlý náhled a Google se podrží do 0,9 s. Stihne-li AI, ukáže se rovnou její překlad. AI, která dorazí víc než 2 s po Googlu, už titulek nepřepíše (uloží se jen do kontextu).
- Jména postav: běžná anglická slova na začátku věty („Nor“, „Then“, „Maybe“…) se už nepovažují za jméno (dřív vzniklo „Nor: Jsou v pořádku…“).

## 0.20.1-fuki20
- TV: opravená hlavička – tlačítka Restart a Vypnout mají pevnou šířku a už nepřekrývají logo ani verzi; Restart má ikonu šipky dokola.
- Česky nahlas čte i nápovědy a návody ve hře (např. chytání ryb).

## 0.20.1-fuki19
- **Česky nahlas:** přeložené titulky čte český hlas (Android převod textu na řeč), jména postav vynechá, nová replika přeruší starou. Když běží AI, hlas chvilku počká na lepší překlad, ať se nečte dvakrát.
  - Telefon → Úpravy: Vypnuto / Mluví televize / Mluví telefon, rychlost Pomaleji / Normálně / Rychleji (s ukázkou).
  - TV: dlaždice „Nahlas“ (místo Jazyka – zatím je jen čeština). Když v TV chybí český hlas, nabídne instalaci.
- Nápověda pole pro AI klíč zná nový formát klíčů AQ.

## 0.20.1-fuki18
- Hra z Xboxu se zjišťuje při připojení a pak každých 5 minut (dotaz na Xbox účet je zdarma, AI se volá jen při změně hry).
- Tlačítko **Změnil jsem hru**: telefon → Úpravy (sekce Překlad), TV → O aplikaci. Zapomene profil staré hry a hned zjistí novou.
- AI úsporně: volba „Jen delší věty“ – krátké repliky přeloží Google zdarma, AI jen věty od 6 slov (telefon → Úpravy, TV → dlaždice AI).

## 0.20.1-fuki17
- Hru pozná Xbox: každou minutu se zjistí, co se na Xboxu hraje (stav účtu na Xbox Live). Podle toho se hned zapne správná tabulka překladů a AI si připraví profil hry (postavy, pojmy) bez čekání na titulky.
- Chytrá doba zobrazení: krátká replika zmizí rychle, dlouhá vydrží déle. V Úpravách volba Kratší / Normálně / Déle (děti).
- Ukazatel rychlosti v Pokročilých: čas čtení a překladu posledního titulku podle zdroje (tabulka, paměť, AI, google…) a průměry za 30 s.

## 0.20.1-fuki16
- Telefon – Úpravy → Pokročilé: posuvník „Pozadí (krytí)“ pro ruční nastavení průhlednosti pozadí za titulky (po 5 %).

## 0.20.1-fuki15
- Pryč staré obrazovky: úvodní načítání a uvítání (betu) nahradila animace Fuki. Výběr jazyka, připojení Xboxu, přihlášení k Microsoft účtu (QR) a čekání na telefon (TV) jsou nově v dlaždicích. Na TV se vše vejde na jednu obrazovku.
- Jazyk překladu: zatím jen čeština (slovenština a další jazyky v backlogu, ukazují se jako „Připravujeme“).
- Dialogy v barvách aplikace (tmavé, ostré rohy, žlutá tlačítka).
- Telefon – Úpravy zjednodušené: náhled herní scény s titulkem, poloha Dole / Uprostřed / Nahoře, velikost písma Malé / Střední / Velké, pozadí Žádné / Poloviční / Plné, co překládat navíc (Nic, Volby odpovědí, Dopisy a SMS, Menu a deník), AI a Tabulky. Rámečky, šířka a jemné posuvníky jsou v „Pokročilých“. Zámky zrušeny.

## 0.20.1-fuki14
- Telefon: displej se při hraní do TV už neztmaví hned. Ztlumí se až po 30 s bez dotyku a dotykem se hned zase rozsvítí, takže jde v klidu nastavovat.

## 0.20.1-fuki13
- Televize: vpravo nahoře tlačítka **Restart** a **Vypnout**. Zastaví překlad, spojení i služby na pozadí, takže aplikace nezůstane v paměti (TCL ji jinak drží). Restart ji spustí znovu načisto.

## 0.20.1-fuki12
- Televize: nová hlavní obrazovka v dlaždicích přesně na jednu obrazovku (bez posouvání): HRÁT (vybrané hned po spuštění), Konzole (Xbox), Telefon, Titulky čte (telefon/televize), Tabulky, a řada Jazyk, Dialogy, Přes hru, AI, Podpořit, O aplikaci. PlayStation skrytý i na TV. Vybraná dlaždice: světlejší, bílý rámeček, žlutá lišta.

## 0.20.1-fuki11 (fuki10 + styl všude)

## 0.20.1-fuki10
- Telefon: nová hlavní obrazovka v dlaždicích (návrh „TitulkyCZ Metro HUD“): Hrát, Konzole, Televize, Čeština, AI překlad, Dialogy, Menu, Tabulky překladů, Vzhled titulků, Verze; písmo Plus Jakarta Sans, ikony Material Symbols. PlayStation v telefonu skrytý.
- Automatická aktualizace: při spuštění kontrola GitHub Releases, nabídka „Aktualizovat“ → stažení a instalace. V „O aplikaci“ tlačítko Zkontrolovat aktualizace.
- AI klíč a nahrání tabulky jdou i z hlavní obrazovky.
- Všechny obrazovky (připojení Xboxu, jazyk, uvítání, TV hlavní obrazovka) v témže stylu: tmavě modré pozadí, ostré rohy, písmo Plus Jakarta Sans, akční tlačítka modře, vybraná položka světlejší se žlutou lištou, malá hlavička místo velkého loga, nenápadné číslo verze místo „BETA“.
- GitHub Actions „Kontrola“: po každém nahrání ověří, že se aplikace sestaví.

## 0.20.1-fuki9
- Podpisová značka Fuki: úvodní animace „Fuki“ při spuštění (jednou za spuštění, ~3,2 s, přeskočit klepnutím nebo OK/Zpět/Enter, při vypnutých animacích v systému se neukáže) a podpis „created by Fuki. | jarek.fuki@gmail.com“ v patičce (TV hlavní obrazovka, telefon – Úpravy).

## 0.20.1-fuki8 – TitulkyCZ_Fuki
- Nový název **TitulkyCZ_Fuki** a nové logo (ikona, banner pro TV, logo v aplikaci).
- O aplikaci: vychází z PlayCaption (Adam Růžička, odkaz na původní projekt), od 0.20.1 samostatně vyvíjí Fuki; QR „Podpora“ = kafe pro Adama.
- Uvítání: hlášení problémů na GitHub (Issues) místo FB skupiny PlayCaption.
- Připraveno pro GitHub: nové README, podepsané vydání přes GitHub Actions (tag v*), versionCode 100 + číslo verze.

## 0.20.1-fuki7
- Telefon – Úpravy: tlačítko „📚 Nahrát tabulku“ → výběr souboru (Stažené soubory, Disk…) → aplikace si tabulku zkopíruje a hned načte. Bez adb.
- Tabulky se načítají už při otevření aplikace, stav v Úpravách se po načtení sám obnoví.

## 0.20.1-fuki6
- Telefon – Úpravy: posuvník „Pozadí“ 0–100 % = jak moc černé pozadí je za titulky na televizi (100 % = jako dřív, 0 % = jen písmo s výrazným stínem). Mění se hned, na TV se při posouvání ukáže ukázkový titulek.

## 0.20.1-fuki5
- Telefon – žlutý rámeček má třetí režim „📋 Menu“: každý řádek v rámečku (menu, úkoly, deník, inventář) se přeloží zvlášť (tabulka → Google) a na televizi se ukáže český seznam vedle rámečku; nemusí být nic zvýrazněné, vydrží, dokud je menu vidět.
- Telefon – nastavení AI přímo v aplikaci (tlačítko „🤖 AI překlad“ v Úpravách): zadat klíč Gemini, zapnout/vypnout. Klíč z aplikace má přednost před local.properties.
- Telefon – v Úpravách stav tabulek hotových překladů (počet vět).

## 0.20.1-fuki4
- Tabulka: rozdělená replika (hra ukáže dlouhou větu ve dvou titulcích) se najde podle začátku/konce věty → hned celá česká věta (test na RDR2: ~85 % půlek správně, ~2 % jiná věta).
- Tabulka podle hry: hlavička souboru „#hra:“ / „#alias:“; když AI pozná hru, používá se jen její tabulka (jiná hra = tabulky vypnuté).
- AI dostane i jména a názvy tak, jak je překládá čeština hry („Miss Grimshaw → slečna Grimshawová“).
- Záznam: každých 30 s „[překlady za 30 s: tabulka 12× ⌀3 ms, ai 5× ⌀820 ms, google …]“.
- Úklid: z repozitáře odstraněny Adamovy testovací záznamy a předávací poznámka.

## 0.20.1-fuki3
- Tabulky hotových překladů (komunitní čeština): soubory *.tsv / *.tsv.gz ve složce Android/data/cz.titulky.tv.fuki/files/tabulky/.
  - Přečtený titulek se v tabulce najde i s drobnou chybou čtení → lidský překlad okamžitě a bez internetu (v záznamu „tabulka“).
  - Když je replika v tabulce, AI se nevolá. Co v tabulce není, překládá Google / AI jako dřív.
- tools/prevod_rdr2.py: převod oficiální angličtiny RDR2 (XML z Nexus mod 10249) + češtiny LokalizaceNET na tabulku.

## 0.20.1-fuki2
- AI překlad s kontextem hry (Google Gemini, vlastní klíč v local.properties: gemini.key=…).
  - Po 8 replikách AI (s vyhledáváním Google) pozná hru a sestaví profil: postavy a jejich rod, místa, termíny, tykání/vykání. Uloží se do files/profil_hry.txt.
  - Každá replika se přeloží s profilem a posledními 6 replikami → správný rod („udělala jsem“), jména, termíny.
  - Běží souběžně s Googlem: nejdřív se ukáže rychlý překlad, AI ho do ~1 s nahradí. Bez klíče se nic nemění.
  - V záznamu řádky „→ AI (… ms)“ a „[AI: hra = …]“.

## 0.20.1-fuki1 (vlastní verze Fuki, ID cz.titulky.tv.fuki)
- Jméno postavy bez dvojtečky („Enki Finally…“, „Nor I did this?“) se pozná a nepřekládá („Ani“ místo „Nor“ už ne); věta se nepřekládá znovu.
- Pomalý překlad starší repliky se už neukáže přes novější titulek.
- Paměť překladů se ukládá na disk (platí i po restartu), až 5000 vět.
- Víc replik naráz se překládá souběžně (3 vlákna).

## 0.20 (versionCode 31)
- PS5 na telefonu: obraz jde z dekodéru přímo do paměti (jako u Xboxu) – čtení titulků 20–40 ms místo ~80 ms. Televize beze změny.
- PS5 na telefonu: kodek H.265 (čistší obraz, ostřejší písmo) – automaticky, jen když ho telefon umí; jinak H.264.
- PS5: poškozený obraz po výpadku Wi-Fi se nečte (méně nesmyslů).
- Spojování rozdělených vět: jen když pokračování přijde hned; tři tečky zůstávají; useknutý titulek se po dočtení přeloží znovu; useknutý začátek („et“ místo „Let“) se přečte znovu.
- Titulky už nepřeblikávají (rychlý offline překlad jen když Google neodpoví do 0,15 s).
- Telefon: po „Připojit“ rámeček „Máš PlayCaption už přihlášenou v televizi?“ (Vyhledat televizi / Nemám televizi).
- Televize: nový návod „Titulky z telefonu“ pro obě cesty spárování telefonu.
- Telefon: Zpět aplikaci nezavře (spojení drží), displej nezhasne, když běží spojení; srozumitelné hlášení, když PS5 drží jiné zařízení.
- Záznam: přehled připojení a odpojení, delší výpis titulků.

## Stará beta (jen televize) – větev `beta-0.9` (už se nevyvíjí)

### 0.9.2 (versionCode 12)
- Lepší čtení: zkomolené slovo z rozmazaného snímku se nepřekládá hned, počká se na ostřejší snímek; úplný nesmysl se na televizi neukáže.
- Slabší televize: čtení titulků běží „v pozadí“ (spojení s PS5 má přednost), úsporný režim se zapne hned po prvním pomalém čtení nebo výpadku.
- Po výpadku obrazu delší pauza (8 s) před novým připojením – méně chyb „already in use“.
- Srozumitelné české zprávy při odpojení („připojím se znovu sám, nic nedělej“).

### 0.9.1 (versionCode 11)
- Televize, které nedávají obraz (Philips, Xiaomi): po 15 s se aplikace sama přepne na jiný způsob čtení obrazu (OpenGL).
- Bez povolení „Zobrazovat přes jiné aplikace“ už aplikace nespadne – ukáže návod.
- Pojistka proti opakovanému pádu po zapnutí televize.
- Poděkování projektu Chiaki / chiaki-ng v aplikaci.

### 0.9 (versionCode 10)
- První veřejná beta.

## Hlavní verze (televize + telefon) – větev `master`

### Beta 0.18 (versionCode 29)
- Oprava: televize už nespadne při předání PS5 telefonu („Translator has been closed“ – např. po přepnutí telefonu na foťák a zpět).
- Telefon se po 1 minutě odpojí od PS5 jen když je zamčený (zhasnutý displej). S otevřenou jinou aplikací (foťák – natáčení videa) překlad běží dál.
- Televize: v kartě „Připojit ke konzoli“ nové tlačítko „+ Přidat Android zařízení“ – okno s QR kódem ke stažení aplikace, 4místným kódem (platí 180 s, odpočet, „Nový kód“) a adresou televize. Po spárování okno samo zmizí.
- Dlaždice „Jen televize“: místo „Funguje každému“ teď „Slabší televize nemusí stíhat“.
- Slabá televize = do 2 GB paměti (dřív pod 1,8 GB) – ale jakmile televize při hraní 10× po sobě přečte titulek rychle, upozornění se už neukazuje. Upozornění má žlutočerné výstražné proužky, tlačítka „Zpět“ / „Pokračovat“.
- Upozornění „Televize má slabý výkon“ (jen na opravdu slabé televizi při volbě „Jen televize“) říká i to, že aplikace může přestat reagovat nebo se ukončit, a doporučuje Android telefon / tablet.
- „Menu a ovládací nápověda“ už nemá Ano/Ne (televize ani telefon) – menu, úkoly a dopisy si každý přeloží žlutým rámečkem v mobilní aplikaci.
- Když aplikace v režimu „Jen televize“ spadne, po dalším otevření se nepřipojí sama dokola: ukáže „⚠️ Aplikace minule spadla“ (výstražné proužky, Zpět / Pokračovat) a televizi si zapamatuje jako slabou.
- Uvítání je kratší a přátelštější („Ahoj a vítej v PlayCaption!“ – beta, zpětná vazba ve Facebook skupině) a při prvním spuštění se ukáže až po výběru jazyka, rovnou v tom jazyce. Nemizí samo – pokračuje se tlačítkem „OK, jdeme na to“.
- Televize: po kliknutí na „Připojit“ nejdřív čitelné upozornění, že s mobilní aplikací (Android telefon / tablet) se v televizi přihlašovat nemusí. Tlačítka „Zpět (budu používat mobilní aplikaci)“ → druhá tabulka „vyber dole dlaždici S telefonem“ (potvrdit OK), a „OK (přihlásit televizi)“.
- Telefon: tlačítko „📺 Vyhledat televizi“ – seznam nalezených televizí, klepnout na svoji a opsat kód. Nenajde-li televizi: „Zkusit znovu“ nebo „Zadat adresu televize“.
- Nespárovaný telefon, který v síti najde SPÁROVANOU televizi s PlayCaption, schová přihlašování („Připojit“) – stačí opsat kód z televize. Nespárovaná televize nevadí: telefon se spáruje s PS5 sám a televize jen ukazuje titulky.

### Beta 0.17 (versionCode 28)
- Režim s telefonem: jméno postavy má na televizi (i v telefonu) barvu jako ve hře – stejně jako v režimu Jen televize („Vivian:“ zeleně). Se starší verzí na druhém zařízení je jméno prostě bílé.

### Beta 0.16 (versionCode 27)
- Navazuje na 0.14 (zase jedna apka pro televize i telefony).
- Titulky jen v telefonu (📱 Telefon): telefon jde otočit na šířku → černá obrazovka jen s velkými titulky přes celou šířku a malou zelenou tečkou „Titulky aktivní“. Obrazovka s posuvníky (📺 Televize) zůstává jen na výšku.

### Beta 0.15 (versionCode 26) – jen pro test na Philipsu, odložená ve složce `NextUpdate_015`
- Dvě apky: TV (navíc „Zapnout přes Usnadnění“ pro televize se schovaným povolením) a Telefon (bez Usnadnění).

### Beta 0.14 (versionCode 25)
- Odebráno „Zapnout přes Usnadnění“ – telefony Xiaomi (HyperOS) kvůli němu verzi 0.13 odmítly nainstalovat („Aplikaci nelze nainstalovat“). Jinak stejné jako 0.13.

### Beta 0.13 (versionCode 24)
- Tlačítko „Povolit zobrazení přes jiné aplikace“ otevře nastavení vždy, když to jde (stránka povolení → seznam aplikací → nastavení PlayCaption); návod až když televize nastavení nemá.
- Zapnutí přes Usnadnění (pro televize se schovaným povolením) – v 0.14 zase odebráno.

### Beta 0.12 (versionCode 23)
- Už by nemělo být potřeba „Vynutit ukončení“: ukončování starého spojení běží zvlášť a nezablokuje hledání PS5; když PS5 v síti není vidět, aplikace se zkusí připojit přímo.
- PS5 se hledá i přímo na známé adrese – na sítích, kde hromadné hledání neprojde (oddělená 2,4/5 GHz Wi-Fi), už není potřeba pořád znovu párovat.
- Žlutý rámeček má nový režim „📄 Text“: přeloží celý text v rámečku najednou (dopis, SMS, popis úkolu) a ukáže ho na televizi jedním oknem.
- Volby odpovědí (žlutá tabulka) se ukážou jen, když hra opravdu ukazuje zvýrazněnou volbu – loga a nápisy (např. na načítací obrazovce) už na televizi nezůstanou viset.
- Při tažení rámečků v mini televizi se už nehýbe celá stránka.
- Visící staré spojení s PS5 (po výpadku Wi-Fi, zhasnutí telefonu…) se před novým připojením vždy ukončí – méně chyb „already in use“.

### Beta 0.11 (versionCode 22)
- Telefon může převzít spárování s PS5 od televize: na televizi „S telefonem“, v telefonu „📺 Převzít spárování z televize“ a opsat 4místný kód z televize. Bez přihlašování účtu a PINu z PS5.

### Beta 0.10 (versionCode 21) – dřívější NextGen
- Nově jedna aplikace pro všechny: „Jen televize“ i „S telefonem“.
- Vše z bety 0.9.2 (lepší čtení, slabší televize, české zprávy).
- Telefon dostává z PS5 ostřejší obraz (720p, víc dat) – přesnější čtení v akčních scénách.
- Přepínač v telefonu [📺 Televize | 📱 Telefon] – kde se ukazují titulky, jedním klepnutím.
- Modrý rámeček „Čtení titulků: RUČNĚ“ – čte se jen tam, kde jsou titulky; posuvníky šířky a výšky.
- Zámečky u zeleného, modrého i žlutého rámečku (zamčený rámeček nejde posunout, posuvníky se schovají).
- Šedá nápověda pod tlačítky; v režimu televize jen malý zelený řádek nahoře, nastavení jde posouvat.
- Nic nezamrzne na televizi: po uspání PS5 telefon smaže nápovědu i volby; pojistky 20 s / 45 s / 5 s.
- Otočení telefonu na bok už nepřeruší posílání do televize (aplikace jen na výšku).
- Slabá televize: aplikace doporučí režim s telefonem a upozorní, když člověk zvolí jen televizi.

### NextGen 0.1 (versionCode 20, jen testovací)
- Režim „S telefonem“, volby odpovědí (žlutý rámeček), úpravy polohy titulků z telefonu.
