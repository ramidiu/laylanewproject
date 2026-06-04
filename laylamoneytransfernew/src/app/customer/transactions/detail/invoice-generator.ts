// Code added by Naresh: Stripe-style invoice PDF generator for the customer
// transaction detail page. Matches the reference design:
// - Layla logo (loaded from /assets/images/layla-logo.webp)
// - "Seamless Global Transfers" tagline
// - Right-aligned Invoice title + meta rows with colons
// - Billed To / From columns
// - 4-row line-item table with description + subtitle
// - Totals block on the right with Subtotal / VAT / Total
// - Footer with payment note and layla.money link
//
// Uses jsPDF loaded on-demand from a public CDN so the Angular build doesn't
// need a new npm dependency. Calls doc.save() to trigger a real browser
// download (no print dialog).

import { TransactionResponse } from '../../../core/models/transaction.model';
import { UserResponse } from '../../../core/models/user.model';

const JSPDF_CDN = 'https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js';
const LOGO_URL = 'assets/images/layla-logo.webp';

// A4 portrait dimensions in points (595 x 842). 40pt outer margin.
const LEFT_X = 40;
const RIGHT_X = 555;
const CONTENT_W = RIGHT_X - LEFT_X;

const COLORS = {
  ink: [17, 24, 39] as [number, number, number],            // near-black body text
  sub: [107, 114, 128] as [number, number, number],         // muted grey
  line: [226, 230, 238] as [number, number, number],        // very light separator
  tableHead: [243, 245, 251] as [number, number, number],   // subtle lavender-blue strip
  accent: [30, 64, 175] as [number, number, number],        // blue used for "BILLED TO" / "FROM" / layla.money
  navy: [27, 58, 107] as [number, number, number]           // Layla brand navy
};

const safe = (v: unknown): string => {
  if (v === null || v === undefined) return 'N/A';
  const s = String(v).trim();
  return s === '' || s === 'null' || s === 'undefined' || s === 'NaN' ? 'N/A' : s;
};

// Currency symbol used in the money cells.
//
// jsPDF's built-in Helvetica / Times / Courier fonts only support the Latin-1
// (WinAnsi) character set. £, €, $, ¥, ¢ render correctly; ₦, ₹, ৳, ₱, ₨, ₵
// do NOT — they show up as tofu boxes (`| 1 0 . 0 0`). To keep the PDF
// reliably readable on every device without shipping a Unicode font, we only
// emit a true symbol when it's in the Latin-1 set and fall back to the ISO
// code prefix otherwise (e.g. "NGN 10.00", "INR 100.00").
const LATIN1_CURRENCY_SYMBOLS: Record<string, string> = {
  GBP: '£',
  USD: '$',
  EUR: '€',
  AUD: 'A$',
  CAD: 'C$',
  NZD: 'NZ$',
  JPY: '¥',
  CNY: '¥'
};
const currencySymbol = (code: string | null | undefined): string => {
  if (!code) return '';
  const up = String(code).toUpperCase();
  return LATIN1_CURRENCY_SYMBOLS[up] || (up + ' ');
};

// Currency/country name lookup (matches the backend receipt countryName mapper).
const CURRENCY_COUNTRY: Record<string, string> = {
  GBP: 'UK', USD: 'USA', EUR: 'Europe', AUD: 'Australia', CAD: 'Canada',
  NZD: 'New Zealand', INR: 'India', PKR: 'Pakistan', BDT: 'Bangladesh',
  NGN: 'Nigeria', GHS: 'Ghana', KES: 'Kenya', PHP: 'Philippines',
  LKR: 'Sri Lanka', NPR: 'Nepal', AED: 'UAE'
};
const currencyCountry = (code: string | null | undefined): string => {
  if (!code) return 'N/A';
  return CURRENCY_COUNTRY[String(code).toUpperCase()] || String(code).toUpperCase();
};

const formatAmount = (v: number | null | undefined, currency: string | null | undefined): string => {
  if (v === null || v === undefined || Number.isNaN(v)) return 'N/A';
  const sym = currencySymbol(currency);
  return `${sym}${v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
};

const formatDate = (iso: string | null | undefined): string => {
  if (!iso) return 'N/A';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return 'N/A';
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
};

const addDays = (iso: string | null | undefined, days: number): string => {
  if (!iso) return 'N/A';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return 'N/A';
  d.setDate(d.getDate() + days);
  return formatDate(d.toISOString());
};

// ─── jsPDF CDN LOADER ────────────────────────────────────────────────────────
let jspdfPromise: Promise<any> | null = null;
function loadJsPdf(): Promise<any> {
  if ((window as any).jspdf?.jsPDF) {
    return Promise.resolve((window as any).jspdf.jsPDF);
  }
  if (jspdfPromise) return jspdfPromise;
  jspdfPromise = new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[src="${JSPDF_CDN}"]`) as HTMLScriptElement | null;
    const script = existing || document.createElement('script');
    if (!existing) {
      script.src = JSPDF_CDN;
      script.async = true;
      document.head.appendChild(script);
    }
    const finish = () => {
      const lib = (window as any).jspdf?.jsPDF;
      if (lib) resolve(lib);
      else reject(new Error('jsPDF failed to initialize'));
    };
    script.addEventListener('load', finish);
    script.addEventListener('error', () =>
      reject(new Error('Unable to load the PDF library. Please check your connection and try again.'))
    );
    if ((window as any).jspdf?.jsPDF) finish();
  });
  return jspdfPromise;
}

// ─── LOGO LOADER ─────────────────────────────────────────────────────────────
let logoDataUri: string | null = null;
async function loadLogo(): Promise<string | null> {
  if (logoDataUri) return logoDataUri;
  try {
    const res = await fetch(LOGO_URL);
    if (!res.ok) return null;
    const blob = await res.blob();
    logoDataUri = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onloadend = () => resolve(reader.result as string);
      reader.onerror = () => reject(new Error('Logo read failed'));
      reader.readAsDataURL(blob);
    });
    return logoDataUri;
  } catch {
    return null;
  }
}

// ─── PUBLIC ENTRY ────────────────────────────────────────────────────────────
export async function generateStripeStyleInvoice(
  txn: TransactionResponse,
  profile: UserResponse | null
): Promise<void> {
  const [jsPDF, logo] = await Promise.all([loadJsPdf(), loadLogo()]);
  const doc = new jsPDF({ unit: 'pt', format: 'a4' });

  drawHeader(doc, txn, logo);
  drawParties(doc, profile);
  const afterTable = drawItemTable(doc, txn);
  drawTotalsAndThanks(doc, afterTable, txn);
  drawFooter(doc);

  const fileName = `invoice-${safe(txn.referenceNumber || txn.id)}.pdf`;
  doc.save(fileName);
}

// ─── HEADER ──────────────────────────────────────────────────────────────────
function drawHeader(doc: any, txn: TransactionResponse, logo: string | null): void {
  // Left: brand block
  if (logo) {
    // Native logo aspect ratio is 1248 x 201 (≈ 6.21).
    const logoW = 220;
    const logoH = logoW * (201 / 1248);
    try {
      doc.addImage(logo, 'PNG', LEFT_X, 32, logoW, logoH);
    } catch {
      drawTextWordmark(doc);
    }
  } else {
    drawTextWordmark(doc);
  }

  // Tagline below the logo
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10);
  doc.setTextColor(...COLORS.sub);
  doc.text('Seamless Global Transfers', LEFT_X, 92);

  // Right: "Invoice" title
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(34);
  doc.setTextColor(...COLORS.ink);
  doc.text('Invoice', RIGHT_X, 70, { align: 'right' });

  // Meta rows: "Label  :  value"
  const labelX = 380;        // left edge of the label block
  const colonX = 470;        // colon column (keeps labels and values aligned)
  const valueX = RIGHT_X;    // right edge for the value (right-aligned)
  const metaRows: [string, string][] = [
    ['Invoice No.', safe(txn.referenceNumber || txn.id)],
    ['Invoice Date', formatDate(txn.createdAt)],
    ['Due Date', addDays(txn.createdAt, 7)]
  ];

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10.5);
  let y = 112;
  metaRows.forEach(([label, value]) => {
    doc.setTextColor(...COLORS.ink);
    doc.text(label, labelX, y);
    doc.text(':', colonX, y);
    doc.text(value, valueX, y, { align: 'right' });
    y += 22;
  });

  // Divider line
  doc.setDrawColor(...COLORS.line);
  doc.setLineWidth(0.5);
  doc.line(LEFT_X, 180, RIGHT_X, 180);
}

function drawTextWordmark(doc: any): void {
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(22);
  doc.setTextColor(...COLORS.navy);
  doc.text('Layla Money Transfer', LEFT_X, 62);
}

// ─── BILLED TO / FROM ────────────────────────────────────────────────────────
function drawParties(doc: any, profile: UserResponse | null): void {
  const top = 212;
  const midX = LEFT_X + CONTENT_W / 2;

  // Section labels in blue
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(10);
  doc.setTextColor(...COLORS.accent);
  doc.text('BILLED TO', LEFT_X, top);
  doc.text('FROM', midX, top);

  // Customer data
  const custName = profile
    ? `${safe(profile.firstName)} ${profile.lastName ?? ''}`.trim()
    : 'N/A';
  const custAddr1 = safe(profile?.addressLine1);
  const custAddr2City = [profile?.city, profile?.country].filter(Boolean).join(', ') || 'N/A';
  const custPostcode = safe(profile?.addressLine2); // postcode is sometimes in addressLine2
  const custEmail = safe(profile?.email);
  const custPhone = safe(profile?.phone);

  const fromLines: string[] = [
    'Layla Money Transfer Ltd.',
    '193 Seven Sisters Road',
    'Finsbury Park, London, N4 3NG',
    'United Kingdom'
  ];
  const fromEmail = 'support@laylamoneytransfer.com';
  const fromPhone = '+44 (0) 203 000 0000';

  // Name — bold
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(13);
  doc.setTextColor(...COLORS.ink);
  doc.text(custName, LEFT_X, top + 28);
  doc.text(fromLines[0], midX, top + 28);

  // Address lines — regular
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10.5);
  const addrStart = top + 50;
  let leftY = addrStart;
  let rightY = addrStart;
  const addrLine = (text: string, x: number, y: number) => {
    doc.splitTextToSize(text, CONTENT_W / 2 - 18).forEach((ln: string, i: number) => {
      doc.text(ln, x, y + i * 14);
    });
  };
  addrLine(custAddr1, LEFT_X, leftY);         leftY += 14;
  addrLine(custAddr2City, LEFT_X, leftY);     leftY += 14;
  addrLine(custPostcode, LEFT_X, leftY);      leftY += 14;

  addrLine(fromLines[1], midX, rightY);       rightY += 14;
  addrLine(fromLines[2], midX, rightY);       rightY += 14;
  addrLine(fromLines[3], midX, rightY);       rightY += 14;

  // Email / Phone rows with colon alignment
  const rowStart = Math.max(leftY, rightY) + 18;
  const emailLabelX = LEFT_X;
  const phoneLabelX = LEFT_X;
  const emailColonX = LEFT_X + 42;
  const emailValueX = LEFT_X + 54;
  const phoneColonX = LEFT_X + 42;
  const phoneValueX = LEFT_X + 54;

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10.5);
  doc.setTextColor(...COLORS.ink);

  // Left column (Billed To)
  doc.text('Email', emailLabelX, rowStart);
  doc.text(':', emailColonX, rowStart);
  doc.text(custEmail, emailValueX, rowStart);
  doc.text('Phone', phoneLabelX, rowStart + 18);
  doc.text(':', phoneColonX, rowStart + 18);
  doc.text(custPhone, phoneValueX, rowStart + 18);

  // Right column (From)
  doc.text('Email', midX, rowStart);
  doc.text(':', midX + 42, rowStart);
  doc.text(fromEmail, midX + 54, rowStart);
  doc.text('Phone', midX, rowStart + 18);
  doc.text(':', midX + 42, rowStart + 18);
  doc.text(fromPhone, midX + 54, rowStart + 18);
}

// ─── ITEM TABLE ──────────────────────────────────────────────────────────────
function drawItemTable(doc: any, txn: TransactionResponse): number {
  const startY = 388;
  const colDescX = LEFT_X + 12;
  const colQtyX = 360;
  const colPriceX = 460;
  const colAmtX = RIGHT_X - 12;

  // Header strip
  doc.setFillColor(...COLORS.tableHead);
  doc.rect(LEFT_X, startY, CONTENT_W, 30, 'F');

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(9);
  doc.setTextColor(...COLORS.sub);
  doc.text('DESCRIPTION', colDescX, startY + 19);
  doc.text('QTY', colQtyX, startY + 19, { align: 'center' });
  doc.text('UNIT PRICE', colPriceX, startY + 19, { align: 'right' });
  doc.text('AMOUNT', colAmtX, startY + 19, { align: 'right' });

  // Rows
  const sendCur = safe(txn.sendCurrency);
  const fromCountry = currencyCountry(txn.sendCurrency);
  const toCountry = currencyCountry(txn.receiveCurrency);

  const sendAmount = txn.sendAmount ?? 0;
  const feeAmount = txn.feeAmount ?? 0;
  const fxMargin = 0;       // placeholder — not exposed in the customer model
  const otherCharges = 0;   // placeholder — reserved for regulatory fees

  type Row = { desc: string; note: string; qty: string; price: string; amount: string };
  const rows: Row[] = [
    {
      desc: 'International Money Transfer',
      note: `${fromCountry} to ${toCountry}`,
      qty: '1',
      price: formatAmount(sendAmount, sendCur),
      amount: formatAmount(sendAmount, sendCur)
    },
    {
      desc: 'Transfer Fee',
      note: 'Service charge for processing',
      qty: '1',
      price: formatAmount(feeAmount, sendCur),
      amount: formatAmount(feeAmount, sendCur)
    },
    {
      desc: 'FX Margin',
      note: 'Exchange rate margin',
      qty: '1',
      price: formatAmount(fxMargin, sendCur),
      amount: formatAmount(fxMargin, sendCur)
    },
    {
      desc: 'Other Charges',
      note: 'Compliance & regulatory fees',
      qty: '1',
      price: formatAmount(otherCharges, sendCur),
      amount: formatAmount(otherCharges, sendCur)
    }
  ];

  let y = startY + 30;
  const rowH = 48;
  rows.forEach((r, idx) => {
    if (idx > 0) {
      doc.setDrawColor(...COLORS.line);
      doc.setLineWidth(0.5);
      doc.line(LEFT_X + 12, y, RIGHT_X - 12, y);
    }

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(11);
    doc.setTextColor(...COLORS.ink);
    doc.text(r.desc, colDescX, y + 20);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    doc.setTextColor(...COLORS.sub);
    doc.text(r.note, colDescX, y + 34);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(11);
    doc.setTextColor(...COLORS.ink);
    doc.text(r.qty, colQtyX, y + 25, { align: 'center' });
    doc.text(r.price, colPriceX, y + 25, { align: 'right' });
    doc.text(r.amount, colAmtX, y + 25, { align: 'right' });

    y += rowH;
  });

  return y + 28;
}

// ─── TOTALS + THANK-YOU BLOCK ────────────────────────────────────────────────
function drawTotalsAndThanks(doc: any, startY: number, txn: TransactionResponse): void {
  const sendCur = safe(txn.sendCurrency);
  const subtotal = txn.sendAmount ?? 0;
  const fee = txn.feeAmount ?? 0;
  const wallet = txn.walletAmountUsed && txn.walletAmountUsed > 0 ? txn.walletAmountUsed : 0;
  const total = (txn.totalDebitAmount ?? (subtotal + fee)) - wallet;

  // Left side — thank you block
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(11);
  doc.setTextColor(...COLORS.ink);
  doc.text('Thank you for choosing Layla Money Transfer.', LEFT_X, startY);
  doc.setTextColor(...COLORS.sub);
  doc.splitTextToSize(
    'If you have any questions, feel free to contact our support team.',
    CONTENT_W / 2
  ).forEach((ln: string, i: number) => {
    doc.text(ln, LEFT_X, startY + 22 + i * 16);
  });

  // Right side — Subtotal / VAT / Total column
  const labelX = 410;
  const valueX = RIGHT_X;
  let y = startY;

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(11);
  doc.setTextColor(...COLORS.sub);
  doc.text('Subtotal', labelX, y);
  doc.setTextColor(...COLORS.ink);
  doc.text(formatAmount(subtotal + fee, sendCur), valueX, y, { align: 'right' });

  y += 20;
  doc.setTextColor(...COLORS.sub);
  doc.text('VAT (0%)', labelX, y);
  doc.setTextColor(...COLORS.ink);
  doc.text(formatAmount(0, sendCur), valueX, y, { align: 'right' });

  if (wallet > 0) {
    y += 20;
    doc.setTextColor(...COLORS.sub);
    doc.text('Wallet Credit', labelX, y);
    doc.setTextColor(...COLORS.ink);
    doc.text(`-${formatAmount(wallet, sendCur)}`, valueX, y, { align: 'right' });
  }

  // Separator line above Total
  y += 14;
  doc.setDrawColor(...COLORS.line);
  doc.setLineWidth(0.5);
  doc.line(labelX, y, RIGHT_X, y);

  // Total row — bold and slightly larger
  y += 22;
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(15);
  doc.setTextColor(...COLORS.ink);
  doc.text('Total', labelX, y);
  doc.text(formatAmount(total, sendCur), valueX, y, { align: 'right' });
}

// ─── FOOTER ──────────────────────────────────────────────────────────────────
function drawFooter(doc: any): void {
  const footerY = 782;
  doc.setDrawColor(...COLORS.line);
  doc.setLineWidth(0.5);
  doc.line(LEFT_X, footerY, RIGHT_X, footerY);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10);
  doc.setTextColor(...COLORS.sub);
  doc.text('Payment via Bank Transfer, Card or E-Wallets.', LEFT_X, footerY + 22);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10);
  doc.setTextColor(...COLORS.accent);
  doc.text('layla.money', RIGHT_X, footerY + 22, { align: 'right' });
}
