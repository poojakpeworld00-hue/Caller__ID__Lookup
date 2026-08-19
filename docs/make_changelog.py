# Change-log PDF for the Caller ID Lookup clone. Written fresh for this project:
# the skill's make_changelog_pdf.py is hardcoded to a different demo. Reuses its
# table_page / section_divider shape and accent styling, fed with the real maps.
import sys, os, re, subprocess
from fpdf import FPDF
from fpdf.fonts import FontFace

SP = os.path.dirname(os.path.abspath(__file__))
DST = "/Users/dreamworld/Documents/Pooja_Apps/2026/08_Aug/Caller_ID_Lookup"
sys.path.insert(0, SP)
from classmap import MAP as CLASSES
from funmap import FUN
from pkgmap import APP, AD, APPMAP, ADMAP

ACCENT=(0,59,255); WHITE=(255,255,255); GREY=(107,114,128); DARK=(14,17,23)   # #003BFF brand
pdf=FPDF(orientation="L", unit="mm", format="A4")
pdf.set_auto_page_break(True, margin=12)
pdf.add_font("arial","","/System/Library/Fonts/Supplemental/Arial.ttf")
pdf.add_font("arial","B","/System/Library/Fonts/Supplemental/Arial Bold.ttf")
HEAD=FontFace(emphasis="BOLD", color=WHITE, fill_color=ACCENT)

def table_page(title, subtitle, headers, rows, widths):
    if not rows: return
    per=26
    for chunk in [rows[i:i+per] for i in range(0,len(rows),per)]:
        pdf.add_page()
        pdf.set_font("arial","B",13); pdf.set_text_color(*ACCENT)
        pdf.cell(0,7,title,new_x="LMARGIN",new_y="NEXT")
        if subtitle:
            pdf.set_font("arial","",8); pdf.set_text_color(*GREY)
            pdf.multi_cell(0,4,subtitle,new_x="LMARGIN",new_y="NEXT")
        pdf.ln(1.5)
        pdf.set_font("arial","",7.6); pdf.set_text_color(20,20,20); pdf.set_draw_color(220,224,230)
        with pdf.table(col_widths=widths, headings_style=HEAD, line_height=4.6,
                       text_align="LEFT", padding=(1.2,1.6)) as t:
            r=t.row()
            for h in headers: r.cell(h)
            for row in chunk:
                r=t.row()
                for c in row: r.cell(str(c))

def section_divider(text, note=""):
    pdf.add_page()
    pdf.ln(52)
    pdf.set_font("arial","B",24); pdf.set_text_color(*ACCENT)
    pdf.cell(0,12,text,align="C",new_x="LMARGIN",new_y="NEXT")
    if note:
        pdf.set_font("arial","",10); pdf.set_text_color(*GREY)
        pdf.multi_cell(0,5,note,align="C")

def pairs(path):
    out=[]
    for l in open(path):
        p=l.split()
        if len(p)==2: out.append(tuple(p))
    return out

# ---------------- cover ----------------
pdf.add_page(); pdf.ln(34)
pdf.set_font("arial","B",26); pdf.set_text_color(*ACCENT)
pdf.cell(0,14,"Caller ID Lookup - Clone Change Log",align="C",new_x="LMARGIN",new_y="NEXT")
pdf.set_font("arial","",12); pdf.set_text_color(*DARK)
pdf.cell(0,7,"Old -> New, every rename and rewrite",align="C",new_x="LMARGIN",new_y="NEXT")
pdf.ln(6)
pdf.set_font("arial","",10); pdf.set_text_color(*GREY)
for line in [
  "Source:  Caller ID Phone Home   com.callerid.phonelookup.home",
  "Clone:   Caller ID Lookup       com.callerid.number.lookup.home",
  "Play listing: Caller ID Lookup Home     Firebase: caller-id-lookup-26014",
  "",
  "Similarity vs source:  byte-identical files 344/795 (43%) -> 116/711 (16%)",
  "                       app strings by char mass 99% -> 59%",
  "                       92 of the 96 remaining are generic Material vector icons",
]:
    pdf.cell(0,5.4,line,align="C",new_x="LMARGIN",new_y="NEXT")

# ---------------- identity ----------------
section_divider("1. Identity")
table_page("Identity","Applied in Stage 2.",["Item","Old","New"],[
 ["applicationId / namespace","com.callerid.phonelookup.home","com.callerid.number.lookup.home"],
 ["Ad module package","com.callerid.adcast","com.callerid.admesh"],
 ["rootProject.name","Caller ID Phone Home","Caller ID Lookup"],
 ["APK archive prefix","CallerIdPhoneHome","CallerIdLookup"],
 ["Theme","Theme.CallerIdPhoneHome","Theme.CallerIdLookup"],
 ["app_name / app_label","Caller ID","Caller ID Lookup"],
 ["Play listing name","Caller ID Phone Home","Caller ID Lookup Home"],
 ["Firebase project","caller-id-phone-home","caller-id-lookup-26014"],
 ["Primary (light)","#0969A8","#003BFF"],
 ["Primary (night)","#6FC6F8","#69B6FF"],
 ["Brand hue","203.8 deg","226.1 deg"],
 ["Launcher icon","flat #0898F5","recoloured #003BFD"],
 ["LightHouse key","sk_69w12...uagwi","sk_i9ekt...vkt"],
],[52,90,90])

# ---------------- classes ----------------
section_divider("2. Classes", f"{len(CLASSES)} top-level declarations renamed; 198 files moved.")
table_page("Class renames","Stage 3b. Names avoid this app's grandparent as well as its parent.",
  ["Old","New"], sorted(CLASSES.items()), [110,110])

# ---------------- sub-packages ----------------
section_divider("3. Sub-packages", f"{len(APPMAP)+len(ADMAP)} package segments renamed.")
table_page("Sub-package renames (app)", APP, ["Old","New"],
  sorted(APPMAP.items()), [110,110])
table_page("Sub-package renames (ad module)", AD, ["Old","New"],
  sorted(ADMAP.items()), [110,110])

# ---------------- layouts / drawables ----------------
lay=pairs(os.path.join(SP,"layoutmap.txt"))
section_divider("4. Layouts", f"{len(lay)} layouts renamed. 13 ad-SDK layouts deliberately untouched.")
table_page("Layout renames","Stage 3c. Each also updates R.layout.x, @layout/x and the XxxBinding class.",
  ["Old","New"], lay, [110,110])

drw=pairs(os.path.join(SP,"drawmap.txt"))
section_divider("5. Drawables", f"{len(drw)} drawables renamed.")
table_page("Drawable renames","Stage 3d. ic_launcher* and the ad-bound assets are excluded.",
  ["Old","New"], drw, [110,110])

# ---------------- view ids ----------------
ids=[tuple(l.rstrip('\n').split('\t')) for l in open(os.path.join(SP,"id_map.txt")) if '\t' in l]
section_divider("6. View IDs", f"{len(ids)} view IDs renamed across 113 XML and 77 Kotlin files.")
table_page("View ID renames","Stage 3f. The 34 ad-layout IDs are excluded and were verified disjoint from these.",
  ["Old","New"], ids, [110,110])

# ---------------- functions ----------------
section_divider("7. Functions", f"{len(FUN)} functions renamed, 390 call sites.")
table_page("Function renames","Stage 3g. loadAd, the SharedPreferences-shaped accessors and all platform overrides were excluded.",
  ["Old","New"], sorted(FUN.items()), [110,110])

# ---------------- logic twists ----------------
section_divider("8. Logic and content")
table_page("Logic twists","Stages 3a and 3h - behaviour preserved, implementation changed.",
  ["Unit","Was","Now"],[
  ["ScreenGlob","map-of-lists scanned per call","lazily built lowercased inverted index; 16 self-mapping rows dropped"],
  ["ScreenGlob.keyFor","while loop over an Iterator","asSequence().firstOrNull"],
  ["ScreenGlob.matches","name","renamed refersTo"],
  ["LocalDigitResolver","runCatching per field","single inline probe helper"],
  ["LocalDigitResolver","line-type when block","lookup table"],
  ["LocalDigitResolver","inline region choice","extracted parseRegion + orNullIfBlank"],
  ["LogViewModel","int day bucket + -1 sentinel","private DayBucket enum carrying its @StringRes"],
  ["LogViewModel","matches(filter, type)","LogScope.admits: CallFlavor? property"],
  ["LogViewModel","manual mutableListOf loop","buildList with pre-sized capacity"],
  ["LogViewModel","repository / allCalls / query","history / source / needle"],
 ],[42,95,95])

table_page("Content rewrite","Stages 4 and 5.",["Area","Detail"],[
 ["Default strings","49 strings of 40+ chars rewritten"],
 ["Locales","29 keys x 11 locales = 319 fresh translations"],
 ["Format specifiers","snapshotted before and diffed after - zero drift across 12 files"],
 ["Brand leaks fixed","82 replacements: CallerID, Caller ID Phone Lookup, calleridphonelookup"],
 ["Policy URLs","now sites.google.com/view/calleridlookuphome - SITE DOES NOT EXIST YET"],
 ["Illustrations","4 webp files hue-rotated +22.5 deg to the new brand"],
 ["Lottie","7 raw JSON files: internal nm layer names replaced, re-serialised compact"],
 ["Short labels","~500 left alone - common vocabulary, no plagiarism weight"],
],[52,180])

# ---------------- restyle / analytics / cleanup ----------------
section_divider("9. Restyle, analytics, cleanup")
table_page("UI restyle","Stage 3i.",["Change","Detail"],[
 ["Corner radii","61 bumps across 58 drawables; full pills untouched (21 remain)"],
 ["Borders","1dp @color/outline on 22 curated card/tile/row/sheet surfaces"],
 ["Not done","oval to squircle - 68 ovals mix avatars with radio dots, needs per-file judgement"],
],[52,180])

table_page("Analytics","Stage 6.",["Change","Detail"],[
 ["boot_completed","new - BootSignalReceiver had no analytics at all"],
 ["gate_overlay_allow","new - nothing logged the SYSTEM_ALERT_WINDOW path"],
 ["gate_full_screen_intent_allow","new - same gap on the FSI path"],
 ["logGateResult()","new counterpart to logPermissionResult for Settings-driven gates"],
 ["28 names normalised","Permission_READ_CALL_LOG_Allow -> perm_read_call_log_allow"],
 ["Verified","all within Firebase's 40-char limit and matching [a-z][a-z0-9_]*"],
],[62,170])

table_page("Cleanup","Stage 7.",["Change","Detail"],[
 ["Comments stripped","34,630 -> 30,467 Kotlin lines; all XML re-parses"],
 ["Dead resources","84 removed, 541 KB - 3 layouts, 5 Lottie, 76 drawables"],
 ["Detector corrected","naive scan flagged 36 layouts including board_home; 94% false positives"],
 ["Cause","layouts are reached via the generated XxxBinding class, not R.layout.*"],
],[52,180])

# ---------------- dependencies ----------------
dep=[l.split() for l in open(os.path.join(SP,"deps.txt")) if l.startswith(("androidx","com."))]
section_divider("10. Dependencies", "Reported only. Nothing was bumped - that needs approval.")
table_page("Dependency report","Google Maven only; Maven Central blocks these queries.",
  ["Artifact","Current","Latest stable",""],
  [[d[0],d[1],d[2]," <- newer" if len(d)>3 else ""] for d in dep],[95,28,32,25])

out=os.path.join(DST,"docs","CHANGE_LOG.pdf")
os.makedirs(os.path.dirname(out),exist_ok=True)
pdf.output(out)
print("wrote", out, os.path.getsize(out),"bytes,", pdf.pages_count if hasattr(pdf,'pages_count') else len(pdf.pages),"pages")
