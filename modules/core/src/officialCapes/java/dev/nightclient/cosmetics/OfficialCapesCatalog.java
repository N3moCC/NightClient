package dev.nightclient.cosmetics;

import java.util.ArrayList;
import java.util.List;

final class OfficialCapesCatalog {
  private OfficialCapesCatalog() {}

  static boolean enabled() {
    return true;
  }

  static String texturePrefix() {
    return "https://textures.minecraft.net/texture/";
  }

  static List<OfficialCapes.Cape> createCatalog() {
    List<OfficialCapes.Cape> list = new ArrayList<OfficialCapes.Cape>();
    list.add(OfficialCapes.entry("snow_golem", "events", 2026, "#E8F4FA", "#B8D6E8", "#1E5F74",
        "snow_golem", null));
    list.add(OfficialCapes.entry("aurora", "events", 2026, "#1A2B57", "#0F1836", "#78F7D6",
        "aurora", "7c418dfbd37412a55e9f1425c9807a591ad10cf71b1edb576a587be9114c277f"));
    list.add(OfficialCapes.entry("hero", "events", 2026, "#2A3E68", "#17233D", "#FFD166", "sword",
        "bb384a1079b9a6f2811520c7991b6e8150d02e5a96457e44509e30822f72f38e"));
    list.add(OfficialCapes.entry("twisted", "events", 2026, "#1E5F5E", "#103233", "#40F2D8",
        "torch", "24aafc451aa2cc34ddc7265211678585c0ef4da4d32edb75ecec1bd8b5408381"));
    list.add(OfficialCapes.entry("builder", "events", 2026, "#8E5734", "#4D2C18", "#FFD180",
        "builder", "2c579968c64c1719740fd8c2a451461879b238002574fce48f7d1a7c36a1c7d4"));
    list.add(OfficialCapes.entry("crafter", "events", 2026, "#8C5A34", "#52311A", "#E3C088",
        "crafter", "479eacefa3cdd7aca94207f36c0dd449653ddf259daf40544a5866baf05eee22"));
    list.add(OfficialCapes.entry("moonlight_trail", "events", 2026, "#17254D", "#0C1329",
        "#6CF2FF", "beacon",
        "fe8a02dfe9e390e44ff33d69feef9d3943f76d3901015bbd50f0b67722d288bd"));
    list.add(OfficialCapes.entry("zombie_horse", "events", 2025, "#3F6D34", "#243F1D", "#B9FF9D",
        "horse", "a3f6e4f14801f3ea55e3d95b9b4ef3b5e8802d947f669de93d6ec4b9354a436b"));
    list.add(OfficialCapes.entry("copper", "events", 2025, "#C96E4B", "#7F3B26", "#4CE0B3",
        "copper", "5e6f3193e74cd16cdd6637d9bae5484e3a37ff2a14c2d157c659a07810b1bdca"));
    list.add(OfficialCapes.entry("yearn", "events", 2025, "#3C4858", "#1E2630", "#6FE3FF", "torch",
        "308b32a9e303155a0b4262f9e5483ad4a22e3412e84fe8385a0bdd73dc41fa89"));
    list.add(OfficialCapes.entry("home", "events", 2025, "#365B8E", "#1D3354", "#9FD3FF", "globe",
        "1de21419009db483900da6298a1e6cbf9f1bc1523a0dcdc16263fab150693edd"));
    list.add(OfficialCapes.entry("menace", "events", 2025, "#7D1F28", "#421015", "#FF7052",
        "portal", "dbc21e222528e30dc88445314f7be6ff12d3aeebc3c192054fba7e3b3f8c77b1"));
    list.add(OfficialCapes.entry("common", "milestones", 2025, "#558B2F", "#3B2818", "#9CCC65",
        "common", "5ec930cdd2629c8771655c60eebeb867b4b6559b0e6d3bc71c40c96347fa03f0"));
    list.add(OfficialCapes.entry("pan", "milestones", 2025, "#C87D32", "#7C4517", "#FFE082",
        "pancape", "28de4a81688ad18b49e735a273e086c18f1e3966956123ccb574034c06f5d336"));
    list.add(OfficialCapes.entry("mojang_office", "events", 2024, "#8C939E", "#4E545E", "#E6ECF5",
        "office", "5c29410057e32abec02d870ecb52ec25fb45ea81e785a7854ae8429d7236ca26"));
    list.add(OfficialCapes.entry("experience", "events", 2024, "#2D6A4F", "#1B4332", "#95D5B2",
        "globe", "7658c5025c77cfac7574aab3af94a46a8886e3b7722a895255fbf22ab8652434"));
    list.add(OfficialCapes.entry("mcc_15", "events", 2024, "#D9A426", "#785310", "#FFF199",
        "crown", "56c35628fe1c4d59dd52561a3d03bfa4e1a76d397c8b9c476c2f77cb6aebb1df"));
    list.add(OfficialCapes.entry("follower", "events", 2024, "#222536", "#11131D", "#FF4B6E",
        "twitch", "569b7f2a1d00d26f30efe3f9ab9ac817b1e6d35f4f3cfb0324ef2d328223d350"));
    list.add(OfficialCapes.entry("purple_heart", "events", 2024, "#5E2B97", "#321557", "#E4A8FF",
        "heart", "cb40a92e32b57fd732a00fc325e7afb00a7ca74936ad50d8e860152e482cfbde"));
    list.add(OfficialCapes.entry("15th_anniversary", "events", 2024, "#388E3C", "#205723",
        "#E8FF99", "creeper",
        "cd9d82ab17fd92022dbd4a86cde4c382a7540e117fae7b9a2853658505a80625"));
    list.add(OfficialCapes.entry("cherry_blossom", "events", 2023, "#F49AC2", "#A3527A", "#FFF0F6",
        "cherry", "afd553b39358a24edfe3b8a9a939fa5fa4faa4d9a9c3d6af8eafb377fa05c2bb"));
    list.add(OfficialCapes.entry("vanilla", "milestones", 2022, "#4E7A38", "#3D2B1F", "#8FD158",
        "common", "f9a76537647989f9a0b6d001e320dac591c359e9e61a31f4ce11c88f207f0ad4"));
    list.add(OfficialCapes.entry("migrator", "milestones", 2021, "#A62626", "#5E1111", "#FFD24D",
        "crown", "2340c0e03dd24a11b15a8b33c2a7e9e32abb2051b2481d0ba7defd635ca7a933"));
    list.add(OfficialCapes.entry("founders", "milestones", 2019, "#B88A32", "#473110", "#FFEE99",
        "creeper", "99aba02ef05ec6aa4d42db8ee43796d6cd50e4b2954ab29f0caeb85f96bf52a1"));

    list.add(OfficialCapes.entry("minecon_2016", "events", 2016, "#27272C", "#131317", "#BA4BFF",
        "enderman", "e7dfea16dc83c97df01a12fabbd1216359c0cd0ea42f9999b6e97c584963e980"));
    list.add(OfficialCapes.entry("minecon_2015", "events", 2015, "#294D5C", "#162A33", "#E6EEF2",
        "golem", "b0cc08840700447322d953a02b965f1d65a13a603bf64b17c803c21446fe1635"));
    list.add(OfficialCapes.entry("minecon_2013", "events", 2013, "#2E5B45", "#1B3629", "#B0BEC5",
        "piston", "153b1a0dfcbae953cdeb6f2c2bf6bf79943239b1372780da44bcbb29273131da"));
    list.add(OfficialCapes.entry("minecon_2012", "events", 2012, "#264B7A", "#152B47", "#FFD54F",
        "pickaxe", "a2e8d97ec79100e90a75d369d1b3ba81273c4f82bc1b737e934eed4a854be1b6"));
    list.add(OfficialCapes.entry("minecon_2011", "events", 2011, "#B72B2B", "#691414", "#FFFFFF",
        "creeper", "953cac8b779fe41383e675ee2b86071a71658f2180f56fbce8aa315ea70e2ed6"));

    list.add(OfficialCapes.entry("mojang_studios", "mojang", 2021, "#CE2A32", "#78141A", "#FFFFFF",
        "mojang", "9e507afc56359978a3eb3e32367042b853cddd0995d17d0da995662913fb00f7"));
    list.add(OfficialCapes.entry("mojang_2015", "mojang", 2015, "#A82A2A", "#631414", "#FFE0D6",
        "mojang", "5786fe99be377dfb6858859f926c4dbc995751e91cee373468c5fbf4865e7151"));
    list.add(OfficialCapes.entry("mojang_classic", "mojang", 2011, "#D97B29", "#7D3F11", "#FFF3D6",
        "classic", "8f120319222a9f4a104e2f5cb97b2cda93199a2ee9e1585cb8d09d6f687cb761"));
    list.add(OfficialCapes.entry("realms_mapmaker", "mojang", 2014, "#32589C", "#1B325E",
        "#F5D782", "map", "17912790ff164b93196f08ba71d0e62129304776d0f347334f8a6eae509f8a56"));
    list.add(OfficialCapes.entry("mojira_mod", "mojang", 2013, "#2D6D8C", "#183C4F", "#B5F2FF",
        "bug", "ae677f7d98ac70a533713518416df4452fe5700365c09cf45d0d156ea9396551"));
    list.add(OfficialCapes.entry("translator", "mojang", 2014, "#2B6947", "#183D29", "#A8FFCC",
        "translator", "1bf91499701404e21bd46b0191d63239a4ef76ebde88d27e4d430ac211df681e"));
    list.add(OfficialCapes.entry("translator_cn", "mojang", 2016, "#A1262D", "#5E1317", "#FFE680",
        "globe_cn", "2262fb1d24912209490586ecae98aca8500df3eff91f2a07da37ee524e7e3cb6"));
    list.add(OfficialCapes.entry("cobalt", "mojang", 2014, "#3B4A73", "#1F2842", "#7AE7FF",
        "cobalt", "4fdbc899e4ca958d118223e9b1a2a1f59be5b36693cdd9dfcb5b624c4c8b13d8"));
    list.add(OfficialCapes.entry("scrolls", "mojang", 2014, "#8C704D", "#4F3D27", "#F2E2BA",
        "scrolls", "3efadf6510961830f9fcc077f19b4daf286d502b5f5aafbd807c7bbffcaca245"));
    list.add(OfficialCapes.entry("document", "mojang", 2022, "#3E546E", "#212D3D", "#CFE6FF",
        "document", "fdcf48f01ec480d1d7cbec27f7ddce48c9da2be6724641109444dae58d4cd013"));

    list.add(OfficialCapes.entry("millionth", "special", 2011, "#253B6E", "#121F40", "#FFE45E",
        "star", "70efffaf86fe5bc089608d3cb297d3e276b9eb7a8f9f2fe6659c23a2d8b18edf"));
    list.add(OfficialCapes.entry("bacon", "special", 2011, "#C94A38", "#7A281E", "#FFE0B2",
        "bacon", "fd14214cd8073059e93d9c626260f5df85e5a959181537119df56cadaf5002cc"));
    list.add(OfficialCapes.entry("dannybstyle", "special", 2011, "#4B3B8C", "#281D52", "#7AF0FF",
        "star", "bcfbe84c6542a4a5c213c1cacf8979b5e913dcb4ad783a8b80e3c4a7d5c8bdac"));
    list.add(OfficialCapes.entry("snowman", "special", 2011, "#4889B5", "#244D6B", "#FFFFFF",
        "snowman", "23ec737f18bfe4b547c95935fc297dd767bb84ee55bfd855144d279ac9bfd9fe"));
    list.add(OfficialCapes.entry("spade", "special", 2012, "#1E222E", "#0E1017", "#E5ECF4", "star",
        "2e002d5e1758e79ba51d08d92a0f3a95119f2f435ae7704916507b6c565a7da8"));
    list.add(OfficialCapes.entry("cheapsh0t", "special", 2012, "#732C2C", "#3D1515", "#FFCC80",
        "sword", "ca29f5dd9e94fb1748203b92e36b66fda80750c87ebc18d6eafdb0e28cc1d05f"));
    list.add(OfficialCapes.entry("prismarine", "special", 2015, "#2D857B", "#164A44", "#94FFEA",
        "prismarine", "d8f8d13a1adf9636a16c31d47f3ecc9bb8d8533108aa5ad2a01b13b1a0c55eac"));
    list.add(OfficialCapes.entry("turtle", "special", 2018, "#3E8E41", "#205223", "#B9FF9D",
        "turtle", "5048ea61566353397247d2b7d946034de926b997d5e66c86483dfb1e031aee95"));
    list.add(OfficialCapes.entry("oxeye", "special", 2021, "#3D6A4E", "#1F3B2B", "#FFF3A1",
        "cherry", "7706b5f5fc90329691e59277dcc66ba20572219fa8e5da472afd5235fad12cc8"));
    list.add(OfficialCapes.entry("birthday", "special", 2012, "#C4457A", "#6B1F3E", "#FFE66D",
        "birthday", "2056f2eebd759cce93460907186ef44e9192954ae12b227d817eb4b55627a7fc"));
    list.add(OfficialCapes.entry("valentine", "special", 2012, "#C92A4C", "#6E1326", "#FFC2D1",
        "valentine", "e578ef995fabcf0a94768f9651ac3aaba30c59ef85d2438e9b3e0cc1d810652b"));
    list.add(OfficialCapes.entry("new_year_2011", "special", 2011, "#2E5B9C", "#162E54", "#9FF0FF",
        "gift", null));
    list.add(OfficialCapes.entry("christmas_2010", "special", 2010, "#9E2A2B", "#541415", "#FFE599",
        "gift", null));
    return list;
  }
}
