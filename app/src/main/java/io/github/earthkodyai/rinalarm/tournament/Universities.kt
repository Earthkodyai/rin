package io.github.earthkodyai.rinalarm.tournament

/**
 * A university a tournament player can name (G.7).
 *
 * @property id what the leaderboard stores (firebase/firestore.rules accepts exactly these ids).
 * @property short the badge on a leaderboard row.
 * @property rank its place in Thailand in the ranking below (1 is first).
 */
data class University(val id: String, val short: String, val english: String, val thai: String, val rank: Int)

/**
 * The 50 Thai universities the tournament lists (the user's pick, 2026-10-03): the top 50 in Thailand of the Ranking
 * Web of Universities (Webometrics), July 2026 edition, as copied by webometrics.pcru.ac.th/article/92. Campuses count
 * as their university. Names and logos only say which university a player picked; the app is not affiliated with or
 * endorsed by any of them. Sources, the logo files (outside the repo, `rin.unis`) and takedown: docs/store/universities.md.
 */
object Universities {
  val all: List<University> =
    listOf(
      University("cu", "CU", "Chulalongkorn University", "จุฬาลงกรณ์มหาวิทยาลัย", 1),
      University("mu", "MU", "Mahidol University", "มหาวิทยาลัยมหิดล", 2),
      University("cmu", "CMU", "Chiang Mai University", "มหาวิทยาลัยเชียงใหม่", 3),
      University("ku", "KU", "Kasetsart University", "มหาวิทยาลัยเกษตรศาสตร์", 4),
      University("kku", "KKU", "Khon Kaen University", "มหาวิทยาลัยขอนแก่น", 5),
      University("psu", "PSU", "Prince of Songkla University", "มหาวิทยาลัยสงขลานครินทร์", 6),
      University("tu", "TU", "Thammasat University", "มหาวิทยาลัยธรรมศาสตร์", 7),
      University("kmutnb", "KMUTNB", "King Mongkut's University of Technology North Bangkok", "มหาวิทยาลัยเทคโนโลยีพระจอมเกล้าพระนครเหนือ", 8),
      University("kmutt", "KMUTT", "King Mongkut's University of Technology Thonburi", "มหาวิทยาลัยเทคโนโลยีพระจอมเกล้าธนบุรี", 9),
      University("ait", "AIT", "Asian Institute of Technology", "สถาบันเทคโนโลยีแห่งเอเชีย", 10),
      University("kmitl", "KMITL", "King Mongkut's Institute of Technology Ladkrabang", "สถาบันเทคโนโลยีพระจอมเกล้าเจ้าคุณทหารลาดกระบัง", 11),
      University("sut", "SUT", "Suranaree University of Technology", "มหาวิทยาลัยเทคโนโลยีสุรนารี", 12),
      University("wu", "WU", "Walailak University", "มหาวิทยาลัยวลัยลักษณ์", 13),
      University("nu", "NU", "Naresuan University", "มหาวิทยาลัยนเรศวร", 14),
      University("swu", "SWU", "Srinakharinwirot University", "มหาวิทยาลัยศรีนครินทรวิโรฒ", 15),
      University("su", "SU", "Silpakorn University", "มหาวิทยาลัยศิลปากร", 16),
      University("rmuti", "RMUTI", "Rajamangala University of Technology Isan", "มหาวิทยาลัยเทคโนโลยีราชมงคลอีสาน", 17),
      University("msu", "MSU", "Mahasarakham University", "มหาวิทยาลัยมหาสารคาม", 18),
      University("buu", "BUU", "Burapha University", "มหาวิทยาลัยบูรพา", 19),
      University("ssru", "SSRU", "Suan Sunandha Rajabhat University", "มหาวิทยาลัยราชภัฏสวนสุนันทา", 20),
      University("bu", "BU", "Bangkok University", "มหาวิทยาลัยกรุงเทพ", 21),
      University("mju", "MJU", "Maejo University", "มหาวิทยาลัยแม่โจ้", 22),
      University("au", "AU", "Assumption University", "มหาวิทยาลัยอัสสัมชัญ", 23),
      University("mfu", "MFU", "Mae Fah Luang University", "มหาวิทยาลัยแม่ฟ้าหลวง", 24),
      University("rsu", "RSU", "Rangsit University", "มหาวิทยาลัยรังสิต", 25),
      University("ru", "RU", "Ramkhamhaeng University", "มหาวิทยาลัยรามคำแหง", 26),
      University("rmutt", "RMUTT", "Rajamangala University of Technology Thanyaburi", "มหาวิทยาลัยเทคโนโลยีราชมงคลธัญบุรี", 27),
      University("pkru", "PKRU", "Phuket Rajabhat University", "มหาวิทยาลัยราชภัฏภูเก็ต", 28),
      University("sru", "SRU", "Suratthani Rajabhat University", "มหาวิทยาลัยราชภัฏสุราษฎร์ธานี", 29),
      University("ubu", "UBU", "Ubon Ratchathani University", "มหาวิทยาลัยอุบลราชธานี", 30),
      University("bru", "BRU", "Buriram Rajabhat University", "มหาวิทยาลัยราชภัฏบุรีรัมย์", 31),
      University("up", "UP", "University of Phayao", "มหาวิทยาลัยพะเยา", 32),
      University("stou", "STOU", "Sukhothai Thammathirat Open University", "มหาวิทยาลัยสุโขทัยธรรมาธิราช", 33),
      University("stiu", "STIU", "Stamford International University", "มหาวิทยาลัยนานาชาติแสตมฟอร์ด", 34),
      University("nida", "NIDA", "National Institute of Development Administration", "สถาบันบัณฑิตพัฒนบริหารศาสตร์", 35),
      University("nrru", "NRRU", "Nakhon Ratchasima Rajabhat University", "มหาวิทยาลัยราชภัฏนครราชสีมา", 36),
      University("cmru", "CMRU", "Chiang Mai Rajabhat University", "มหาวิทยาลัยราชภัฏเชียงใหม่", 37),
      University("snru", "SNRU", "Sakon Nakhon Rajabhat University", "มหาวิทยาลัยราชภัฏสกลนคร", 38),
      University("mcu", "MCU", "Mahachulalongkornrajavidyalaya University", "มหาวิทยาลัยมหาจุฬาลงกรณราชวิทยาลัย", 39),
      University("tsu", "TSU", "Thaksin University", "มหาวิทยาลัยทักษิณ", 40),
      University("yru", "YRU", "Yala Rajabhat University", "มหาวิทยาลัยราชภัฏยะลา", 41),
      University("rmutk", "RMUTK", "Rajamangala University of Technology Krungthep", "มหาวิทยาลัยเทคโนโลยีราชมงคลกรุงเทพ", 42),
      University("lpru", "LPRU", "Lampang Rajabhat University", "มหาวิทยาลัยราชภัฏลำปาง", 43),
      University("hcu", "HCU", "Huachiew Chalermprakiet University", "มหาวิทยาลัยหัวเฉียวเฉลิมพระเกียรติ", 44),
      University("kru", "KRU", "Kanchanaburi Rajabhat University", "มหาวิทยาลัยราชภัฏกาญจนบุรี", 45),
      University("sdu", "SDU", "Suan Dusit University", "มหาวิทยาลัยสวนดุสิต", 46),
      University("dpu", "DPU", "Dhurakij Pundit University", "มหาวิทยาลัยธุรกิจบัณฑิตย์", 47),
      University("cpru", "CPRU", "Chaiyaphum Rajabhat University", "มหาวิทยาลัยราชภัฏชัยภูมิ", 48),
      University("rmutto", "RMUTTO", "Rajamangala University of Technology Tawan-ok", "มหาวิทยาลัยเทคโนโลยีราชมงคลตะวันออก", 49),
      University("utcc", "UTCC", "University of the Thai Chamber of Commerce", "มหาวิทยาลัยหอการค้าไทย", 50),
    )

  /** The picker's order (the user's pick): A to Z by English name. */
  val alphabetical: List<University> = all.sortedBy { it.english.lowercase() }

  private val byId = all.associateBy { it.id }

  operator fun get(id: String?): University? = id?.let(byId::get)

  /** The logo in the app's assets when the build has it (`rin.unis`); the badge falls back to [University.short]. */
  fun logoAsset(id: String): String = "unis/$id.png"
}
