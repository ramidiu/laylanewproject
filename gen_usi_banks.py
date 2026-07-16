#!/usr/bin/env python3
# Generates a Flyway migration that rebuilds bank_database + mobile_money_services
# for every USI corridor country from USI's live getDeliveryBanks (exact names USI accepts).
import sys, urllib.request, urllib.parse, re, html
from xml.dom import minidom

ENV = "/opt/layla-live/.env"
def env(k):
    for line in open(ENV):
        if line.startswith(k + "="):
            return line.split("=", 1)[1].strip()
    return ""

BASE = env("USI_URL").rstrip("/")
CREDS = {"username": env("USI_USERNAME"), "password": env("USI_PASSWORD"),
         "pin": env("USI_PIN"), "agent_name": env("USI_AGENT_NAME")}

# ISO code -> USI dest_country name
COUNTRIES = [("EG", "Egypt"), ("SA", "Saudi Arabia"), ("UG", "Uganda"),
             ("TR", "Turkey"), ("AE", "United Arab Emirates")]

MOBILE_KEYS = ["airtel", "mtn", "africel", "smile", "mobile network",
               "mobile wallet", "ut mobile", "momo"]

def fetch_banks(country):
    data = urllib.parse.urlencode({**CREDS, "dest_country": country}).encode()
    req = urllib.request.Request(BASE + "/deliveryBank/getDeliveryBanks", data=data)
    xml = urllib.request.urlopen(req, timeout=40).read()
    doc = minidom.parseString(xml)
    names = []
    for el in doc.getElementsByTagName("delivery_bank"):
        n = el.getElementsByTagName("name")
        if not n or not n[0].firstChild:
            continue
        name = html.unescape(n[0].firstChild.nodeValue).strip()
        name = re.sub(r"\s+", " ", name)
        # skip cash-pickup pseudo entries and USI's duplicate uppercase "FMT" variants
        if not name or "cash pickup" in name.lower() or re.search(r"\bFMT\b", name):
            continue
        names.append(name)
    # dedupe preserving order, case-insensitive
    seen, out = set(), []
    for n in names:
        k = n.lower()
        if k not in seen:
            seen.add(k); out.append(n)
    return out

def is_mobile(name):
    l = name.lower()
    return any(k in l for k in MOBILE_KEYS)

def sql_str(s):
    return "'" + s.replace("'", "''") + "'"

print("-- ============================================================================")
print("-- V555: Reconcile bank_database + mobile_money_services to USI's EXACT delivery")
print("--       bank names for every USI corridor country. USI matches payouts by bank")
print("--       NAME and REJECTS names it doesn't recognise, so the customer picker must")
print("--       offer only USI's exact names (source: live getDeliveryBanks).")
print("--       Old non-USI rows are DEACTIVATED (is_active=0), not deleted, so existing")
print("--       beneficiaries (bank stored as text) are unaffected.")
print("-- ============================================================================")
print()
for iso, cname in COUNTRIES:
    banks = fetch_banks(cname)
    mob = [b for b in banks if iso == "UG" and is_mobile(b)]
    bnk = [b for b in banks if b not in mob]
    print(f"-- ---- {cname} ({iso}): {len(bnk)} banks" + (f", {len(mob)} mobile operators" if mob else "") + " ----")
    print(f"UPDATE bank_database SET is_active=0 WHERE country_code={sql_str(iso)};")
    rows = ",\n".join(
        f"({sql_str(iso)}, {sql_str(b)}, NULL, NULL, 'Any Branch', NULL, 1)" for b in bnk)
    print("INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city, is_active) VALUES")
    print(rows + ";")
    if mob:
        print(f"UPDATE mobile_money_services SET is_active=0 WHERE country_code={sql_str(iso)};")
        mrows = ",\n".join(
            f"({sql_str(iso)}, {sql_str(cname)}, {sql_str(b)}, 1)" for b in mob)
        print("INSERT INTO mobile_money_services (country_code, country_name, service_name, is_active) VALUES")
        print(mrows + ";")
    print()
