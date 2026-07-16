/**
 * Best-effort Arabic → Latin transliteration for name fields.
 *
 * Money-transfer names are normally entered in English; some users type Arabic.
 * This converts Arabic script to a readable Latin spelling so beneficiary/sender
 * names stay consistent across the app. Common Arabic/Sudanese given names use a
 * dictionary for correct vowels (محمد → Mohamed); anything else falls back to a
 * letter-by-letter mapping. The spelling is approximate and may not match an
 * official/ID spelling — it is only meant to keep names in Latin script.
 *
 * Arabic detection uses numeric code points (not regex ranges) so it is robust
 * to source-encoding round-trips.
 */

// Whole-word dictionary for common names (Arabic omits short vowels, so a plain
// letter map gives "Mhmd"/"Ahmd"; this fixes the frequent ones). Keys are the
// diacritic-stripped Arabic token.
const WORDS: { [k: string]: string } = {
  'محمد': 'Mohamed', 'احمد': 'Ahmed', 'أحمد': 'Ahmed', 'محمود': 'Mahmoud',
  'حسن': 'Hassan', 'حسين': 'Hussein', 'علي': 'Ali', 'عمر': 'Omar',
  'عثمان': 'Othman', 'ابراهيم': 'Ibrahim', 'إبراهيم': 'Ibrahim', 'اسماعيل': 'Ismail',
  'يوسف': 'Yousif', 'ادريس': 'Idris', 'إدريس': 'Idris', 'خالد': 'Khalid',
  'صلاح': 'Salah', 'مصطفى': 'Mustafa', 'طارق': 'Tariq', 'معتصم': 'Mutasim',
  'معاذ': 'Muaz', 'حمزة': 'Hamza', 'بشير': 'Bashir', 'نصر': 'Nasr',
  'سليمان': 'Suliman', 'الطيب': 'Eltayeb', 'الصادق': 'Elsadig', 'الامين': 'Elamin',
  'الأمين': 'Elamin', 'بكري': 'Bakri', 'آدم': 'Adam', 'ادم': 'Adam',
  'جابر': 'Jaber', 'حبيب': 'Habib', 'دين': 'Din', 'نور': 'Noor',
  'الفاتح': 'Elfatih', 'الهادي': 'Elhadi', 'عبدالله': 'Abdullah',
  'عبدالرحمن': 'Abdelrahman', 'عبدالقادر': 'Abdelqader', 'عبدالعزيز': 'Abdelaziz',
  'عبدالرحيم': 'Abdelrahim', 'عبدالرؤوف': 'Abderrauf',
  'فاطمة': 'Fatima', 'عائشة': 'Aisha', 'مريم': 'Mariam', 'زينب': 'Zainab',
  'سارة': 'Sara', 'ساره': 'Sara', 'هبة': 'Hiba', 'اسراء': 'Israa',
  'صديق': 'Siddig', 'الصديق': 'Elsiddig', 'موسى': 'Musa', 'عيسى': 'Issa',
  'يعقوب': 'Yagoub', 'سعد': 'Saad', 'سعيد': 'Saeed', 'ربيع': 'Rabie',
  'الطاهر': 'Eltahir', 'النور': 'Elnour', 'بابكر': 'Babiker', 'كمال': 'Kamal'
};

// Letter map (fallback). Solar/lunar rules ignored; reads acceptably for names.
const LETTERS: { [k: string]: string } = {
  'ا': 'a', 'أ': 'a', 'إ': 'i', 'آ': 'a', 'ٱ': 'a', 'ٲ': 'a', 'ٳ': 'i',
  'ب': 'b', 'ت': 't', 'ث': 'th', 'ج': 'j', 'ح': 'h', 'خ': 'kh',
  'د': 'd', 'ذ': 'dh', 'ر': 'r', 'ز': 'z', 'س': 's', 'ش': 'sh',
  'ص': 's', 'ض': 'd', 'ط': 't', 'ظ': 'z', 'ع': 'a', 'غ': 'gh',
  'ف': 'f', 'ق': 'q', 'ك': 'k', 'گ': 'g', 'ل': 'l', 'م': 'm', 'ن': 'n',
  'ه': 'h', 'ة': 'a', 'و': 'w', 'ؤ': 'w', 'ي': 'y', 'ى': 'a', 'ئ': 'y',
  'ء': '', 'پ': 'p', 'چ': 'ch', 'ژ': 'zh', 'ڤ': 'v'
};

const DIGITS: { [k: string]: string } = {
  '٠': '0', '١': '1', '٢': '2', '٣': '3', '٤': '4', '٥': '5', '٦': '6', '٧': '7', '٨': '8', '٩': '9',
  '۰': '0', '۱': '1', '۲': '2', '۳': '3', '۴': '4', '۵': '5', '۶': '6', '۷': '7', '۸': '8', '۹': '9'
};

const PREFIX_ABDAL = 'عبدال';

/** Arabic letter? (Arabic 0x600–0x6FF, Arabic Supplement 0x750–0x77F). */
function isArabicCode(c: number): boolean {
  return (c >= 0x600 && c <= 0x6FF) || (c >= 0x750 && c <= 0x77F);
}

/** Combining tashkeel/diacritic or tatweel that should be dropped before mapping. */
function isDiacritic(c: number): boolean {
  return (c >= 0x610 && c <= 0x61A) || (c >= 0x64B && c <= 0x65F)
      || c === 0x670 || (c >= 0x6D6 && c <= 0x6ED) || c === 0x640;
}

export function hasArabic(input: string): boolean {
  if (!input) return false;
  for (let i = 0; i < input.length; i++) {
    if (isArabicCode(input.charCodeAt(i))) return true;
  }
  return false;
}

function stripDiacritics(input: string): string {
  let out = '';
  for (const ch of input) {
    if (!isDiacritic(ch.charCodeAt(0))) out += ch;
  }
  return out;
}

/** Map a single token letter-by-letter, lowercased Latin. */
function letterMap(token: string): string {
  let out = '';
  for (const ch of token) {
    if (LETTERS[ch] !== undefined) out += LETTERS[ch];
    else if (DIGITS[ch] !== undefined) out += DIGITS[ch];
    else out += ch;
  }
  return out;
}

function capitalize(s: string): string {
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
}

/** Transliterate one whitespace-delimited token. */
function transliterateToken(token: string): string {
  if (!hasArabic(token)) return token; // already Latin / punctuation
  if (WORDS[token]) return WORDS[token];
  // "عبدال…" compound (Abd al-…) → Abdel + rest
  if (token.startsWith(PREFIX_ABDAL) && token.length > PREFIX_ABDAL.length) {
    return 'Abdel' + letterMap(token.slice(PREFIX_ABDAL.length));
  }
  return capitalize(letterMap(token));
}

export function arabicToLatin(input: string): string {
  if (!input || !hasArabic(input)) return input;
  const stripped = stripDiacritics(input);
  const tokens = stripped.split(/(\s+)/); // keep whitespace separators
  return tokens
    .map(t => (/^\s+$/.test(t) ? ' ' : transliterateToken(t)))
    .join('')
    .replace(/\s+/g, ' ')
    .trim();
}
