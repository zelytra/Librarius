/**
 * The countries a member may say they live in (#573): ISO 3166-1 alpha-2, exactly the codes the
 * API accepts (`java.util.Locale.getISOCountries()`, 249 of them).
 */
export const COUNTRY_CODES: readonly string[] = (
  "AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE " +
  "BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD " +
  "CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM " +
  "DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF " +
  "GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU " +
  "ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN " +
  "KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME " +
  "MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA " +
  "NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM " +
  "PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI " +
  "SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK " +
  "TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI " +
  "VN VU WF WS YE YT ZA ZM ZW"
).split(" ");

/** A country's name in the interface language; the code itself when the runtime cannot name it. */
export function countryName(code: string, language: string): string {
  try {
    return (
      new Intl.DisplayNames([language], { type: "region" }).of(code) ?? code
    );
  } catch {
    return code;
  }
}

/** Every country as an option, sorted by its name in the interface language. */
export function countryOptions(
  language: string
): { code: string; name: string }[] {
  const collator = new Intl.Collator(language);
  return COUNTRY_CODES.map((code) => ({
    code,
    name: countryName(code, language),
  })).sort((a, b) => collator.compare(a.name, b.name));
}
