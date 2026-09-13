#!/usr/bin/env python3
"""Builds inkhorn/app/src/main/assets/story.json. Prose lives here; numbers are tuned by BalanceSimTest."""
import json, re, sys

OUT = __import__("os").path.join(__import__("os").path.dirname(__import__("os").path.abspath(__file__)), "..", "app", "src", "main", "assets", "story.json")

def smart(s):
    s = re.sub(r'(^|[\s(\[])"', r'\1“', s)
    s = s.replace('"', '”')
    s = s.replace("'", '’')
    return s

GROWTH = float(__import__("os").environ.get("GROWTH", "1.2"))

def E(kind, target, factor):
    return {"kind": kind, "target": target, "factor": factor}

resources = [
    {"id": "vellum", "one": "skin of vellum", "many": "skins of vellum", "gained": "scraped", "chapter": 1},
    {"id": "folios", "one": "folio", "many": "folios", "gained": "copied", "chapter": 1},
    {"id": "pennies", "one": "penny", "many": "pennies", "gained": "earned", "chapter": 1},
    {"id": "ink", "one": "horn of ink", "many": "horns of ink", "gained": "ground", "chapter": 2},
    {"id": "books", "one": "book", "many": "books", "gained": "bound", "chapter": 3},
    {"id": "renown", "one": "renown", "many": "renown", "gained": "earned", "chapter": 4},
    {"id": "paper", "one": "ream of paper", "many": "reams of paper", "gained": "made", "chapter": 5},
    {"id": "type", "one": "sort of type", "many": "sorts of type", "gained": "cast", "chapter": 5},
]

actions = [
    {"id": "copy", "label": "Copy a line", "chapter": 1, "cooldown": 1.0,
     "gains": {"folios": 0.2}, "doneLine": "Copied a line"},
    {"id": "sell", "label": "Sell the page", "chapter": 1, "cooldown": 1.0,
     "costs": {"folios": 1}, "gains": {"pennies": 3}, "revealAt": {"taps": 5},
     "revealLine": "A carter at the abbey gate will pay three pennies for a finished page.",
     "doneLine": "Sold the page"},
    {"id": "vellum", "label": "Buy vellum", "chapter": 1, "cooldown": 1.0,
     "costs": {"pennies": 4}, "gains": {"vellum": 5}, "revealAt": {"pennies": 40},
     "revealLine": "The tanner's yard sells scraped skins, five for four pennies. A good scrivener will want them.",
     "doneLine": "Bought vellum"},
    {"id": "grind", "label": "Grind oak galls", "chapter": 2, "cooldown": 2.0,
     "costs": {"pennies": 2}, "gains": {"ink": 1},
     "revealLine": "Oak galls, green vitriol and gum. The mortar is heavy and the ink is black as a rook.",
     "doneLine": "Ground oak galls"},
    {"id": "sew", "label": "Sew a gathering", "chapter": 3, "cooldown": 2.0,
     "costs": {"folios": 4, "ink": 1}, "gains": {"books": 0.25},
     "revealLine": "Four folios folded, pricked and sewn make a gathering. Four gatherings make a book.",
     "doneLine": "Sewed a gathering"},
    {"id": "read", "label": "Read aloud at the door", "chapter": 4, "cooldown": 3.0,
     "costs": {}, "gains": {"renown": 1},
     "revealLine": "Nobody in the lane can read, but everyone stops to listen.",
     "doneLine": "Read aloud at the door"},
    {"id": "pull", "label": "Pull the press", "chapter": 5, "cooldown": 2.0,
     "costs": {"paper": 1, "ink": 1}, "gains": {"folios": 8},
     "revealLine": "Pieter shows you the bar. Ink the forme, lay the sheet, pull. Eight pages at a stroke.",
     "doneLine": "Pulled the press"},
    {"id": "cry", "label": "Cry the new books", "chapter": 6, "cooldown": 3.0,
     "costs": {"books": 2}, "gains": {"pennies": 400, "renown": 4},
     "revealLine": "The market cross is the best shop in any town.",
     "doneLine": "Cried the new books"},
]

workers_list = [
    {"id": "novice", "one": "novice", "many": "novices", "chapter": 1, "cost": {"pennies": 12}, "growth": GROWTH,
     "produces": {"folios": 0.05}, "revealAt": {"pennies": 15},
     "revealLine": "A novice from the abbey school asks for work. The workshop is open.",
     "about": "Copies slowly and blots often, on scraps nobody else wants."},
    {"id": "tanner", "one": "tanner's girl", "many": "tanner's girls", "chapter": 1, "cost": {"pennies": 40}, "growth": GROWTH,
     "produces": {"vellum": 0.1}, "revealAt": {"pennies": 60},
     "revealLine": "Agnes, the tanner's girl, offers to bring skins every morning for a wage.",
     "about": "Brings scraped skins from the tannery every morning."},
    {"id": "pedlar", "one": "pedlar", "many": "pedlars", "chapter": 1, "cost": {"pennies": 9}, "growth": GROWTH,
     "produces": {"pennies": 0.3}, "consumes": {"folios": 0.1}, "revealAt": {"pennies": 6},
     "revealLine": "Hamo the pedlar will carry pages to the fairs, if you will trust him with them.",
     "about": "Sells loose pages at fairs and markets."},
    {"id": "scrivener", "one": "scrivener", "many": "scriveners", "chapter": 1, "cost": {"pennies": 90, "vellum": 20}, "growth": GROWTH,
     "produces": {"folios": 0.3}, "revealAt": {"pennies": 120},
     "revealLine": "A scrivener who lost his post at the manor court asks to sit at your second desk.",
     "about": "A trained hand. Fast, and careful with the rubrics. Hired with a stock of good vellum."},
    {"id": "gatherer", "one": "gall gatherer", "many": "gall gatherers", "chapter": 2, "cost": {"pennies": 80}, "growth": GROWTH,
     "produces": {"ink": 0.5},
     "revealLine": "Children from the lane will pick oak galls for a penny a basket.",
     "about": "Picks galls and grinds them into ink."},
    {"id": "miller", "one": "ink miller", "many": "ink millers", "chapter": 2, "cost": {"pennies": 600}, "growth": GROWTH,
     "produces": {"ink": 2.0, "pennies": 1.5}, "consumes": {}, "revealAt": {"ink": 60},
     "revealLine": "Aldous says his millstones would grind galls as well as barley, and sell the surplus to the cathedral.",
     "about": "Grinds ink at the river mill and sells what you do not use."},
    {"id": "binder", "one": "binder", "many": "binders", "chapter": 3, "cost": {"pennies": 900}, "growth": GROWTH,
     "produces": {"books": 0.1}, "consumes": {"folios": 1.6, "ink": 0.4, "vellum": 0.1},
     "revealLine": "A binder from York sets up his sewing frame by your window.",
     "about": "Sews gatherings and covers them in vellum. Uses folios, ink and skins."},
    {"id": "bookseller", "one": "bookseller", "many": "booksellers", "chapter": 3, "cost": {"pennies": 1500}, "growth": GROWTH,
     "produces": {"pennies": 20.0}, "consumes": {"books": 0.08}, "revealAt": {"books": 3},
     "revealLine": "Margery Stall opens a bookshop under your sign on market days.",
     "about": "Sells bound books to canons, lawyers and merchants."},
    {"id": "librarian", "one": "librarian", "many": "librarians", "chapter": 4, "cost": {"pennies": 6000, "books": 10}, "growth": GROWTH,
     "produces": {"renown": 0.6}, "consumes": {"books": 0.01},
     "revealLine": "The lending room needs a keeper who can find a book and remember who took it.",
     "about": "Keeps the lending room and the borrowers' roll."},
    {"id": "lector", "one": "lector", "many": "lectors", "chapter": 4, "cost": {"pennies": 20000, "books": 25}, "growth": GROWTH,
     "produces": {"renown": 3.0}, "consumes": {"ink": 1.0}, "revealAt": {"renown": 150},
     "revealLine": "Brother Osric offers to teach letters in the evenings, if you keep him in ink.",
     "about": "Teaches reading in the lending room at night."},
    {"id": "papermaker", "one": "papermaker", "many": "papermakers", "chapter": 5, "cost": {"pennies": 40000}, "growth": GROWTH,
     "produces": {"paper": 1.0},
     "revealLine": "Rags beaten to pulp, laid on a wire mould, pressed and hung. Paper, cheaper than any skin.",
     "about": "Beats rags into paper at the old fulling mill."},
    {"id": "founder", "one": "typefounder", "many": "typefounders", "chapter": 5, "cost": {"pennies": 70000}, "growth": GROWTH,
     "produces": {"type": 1.0}, "revealAt": {"paper": 20},
     "revealLine": "Pieter carves a punch for the letter a, and then for every other letter.",
     "about": "Casts lead type from Pieter's punches."},
    {"id": "pressman", "one": "pressman", "many": "pressmen", "chapter": 5, "cost": {"pennies": 150000, "renown": 600}, "growth": GROWTH,
     "produces": {"folios": 40.0}, "consumes": {"paper": 1.2, "ink": 1.5, "type": 0.15}, "revealAt": {"type": 10},
     "revealLine": "Two men to a press: one inks, one pulls. They work in shirtsleeves even in winter.",
     "about": "Works the press. Uses paper, ink and wears out type."},
    {"id": "carter", "one": "carter", "many": "carters", "chapter": 6, "cost": {"pennies": 600000, "renown": 4000}, "growth": GROWTH,
     "produces": {"pennies": 700.0, "renown": 12.0}, "consumes": {"books": 1.5},
     "revealLine": "Wagons, oxen and men who know the roads to every town on the Sele.",
     "about": "Carries books to the towns downriver."},
]

ORDER = ["tanner", "novice", "scrivener", "gatherer", "miller", "papermaker", "founder", "pressman", "binder", "librarian", "lector", "carter", "bookseller", "pedlar"]
workers = sorted(workers_list, key=lambda w: ORDER.index(w["id"]))

def U(id, name, period, chapter, cost, effects, effectText, **kw):
    d = {"id": id, "name": name, "period": period, "chapter": chapter, "cost": cost, "effects": effects, "effectText": effectText}
    d.update(kw)
    return d

W = lambda w: "worker:" + w
A = lambda a: "action:" + a

upgrades = [
    # Chapter one: The Quill
    U("goose_quills", "Goose quills", "Cut from the left wing, which curves away from a right hand.", 1, {"pennies": 15},
      [E("throughput", A("copy"), 2)], "Copy two lines at a time.", revealAt={"pennies": 8}),
    U("pumice", "Pumice and chalk", "A skin rubbed smooth takes ink without feathering.", 1, {"pennies": 45},
      [E("throughput", W("novice"), 1.5)], "Novices copy half as much again.", revealAt={"pennies": 25}),
    U("stall", "A stall on feast days", "Saints' days bring the whole valley to the market.", 1, {"pennies": 70},
      [E("yield", W("pedlar"), 1.4), E("yield", A("sell"), 1.4)], "Pages sell for more.", revealAt={"pennies": 45}),
    U("palimpsest", "Scraped palimpsests", "Old accounts scraped back to the skin. Nobody will miss the tithe rolls of 1310.", 1, {"pennies": 110},
      [E("cost", W("scrivener"), 0.75)], "Scriveners cost a quarter less to hire.", revealAt={"pennies": 90}),
    U("pricking", "A pricking wheel", "Rules every line of a quire at once.", 1, {"pennies": 220},
      [E("throughput", W("scrivener"), 1.6)], "Scriveners copy more than half as much again.", revealAt={"pennies": 140}),
    U("tanner_contract", "A contract with the tannery", "Agnes's father drives a hard bargain and keeps it.", 1, {"pennies": 180},
      [E("yield", W("tanner"), 1.5)], "Each penny buys half as much vellum again.", revealAt={"pennies": 120}),
    U("window", "A lectern by the window", "North light, steady all day, and no candle smoke.", 1, {"pennies": 320},
      [E("throughput", W("novice"), 1.5), E("throughput", W("scrivener"), 1.3)], "All copyists work faster.", revealAt={"pennies": 200}),
    U("fairs", "The fair at St Giles", "Nine days of trade, and every stall wants something to read.", 1, {"pennies": 400},
      [E("throughput", W("pedlar"), 1.8)], "Pedlars sell almost twice as many pages.", revealAt={"pennies": 260}),

    # Chapter two: The Ink Mill
    U("vitriol", "Green vitriol", "Copperas from the coast turns gall water from brown to black.", 2, {"pennies": 600},
      [E("yield", A("grind"), 2), E("throughput", W("gatherer"), 1.3)], "Galls give twice the ink by hand.", revealAt={"pennies": 400}),
    U("gum", "Gum arabic", "A pinch keeps ink on the page instead of in the gutter.", 2, {"pennies": 900, "ink": 40},
      [E("throughput", W("scrivener"), 1.5)], "Scriveners copy half as much again.", revealAt={"ink": 20}),
    U("baskets", "Wicker baskets", "Woven by the gatherers' mothers, who charge for them.", 2, {"pennies": 1400},
      [E("throughput", W("gatherer"), 1.6)], "Gatherers bring more galls.", revealAt={"pennies": 900}),
    U("millrace", "A deeper millrace", "Dug in a dry August, when the Sele runs low.", 2, {"pennies": 5000, "ink": 150},
      [E("throughput", W("miller"), 1.6)], "The mill grinds more than half as much again.", revealAt={"ink": 100}),
    U("cathedral_order", "The cathedral's standing order", "Canon Hugh signs for forty horns a quarter.", 2, {"pennies": 3500, "ink": 250},
      [E("yield", W("miller"), 1.25), E("yield", W("pedlar"), 1.3)], "The mill and the pedlars both earn more.", revealAt={"ink": 150}),
    U("second_desk", "A second scriptorium", "The old weaving shed, whitewashed, with six desks.", 2, {"pennies": 4500},
      [E("throughput", W("novice"), 2), E("throughput", W("scrivener"), 1.5)], "Every copyist has room to work.", revealAt={"pennies": 3000}),
    U("iron_gall", "Iron-gall recipe", "Written in the margin of a Bede, in a hand nobody knows.", 2, {"pennies": 8000, "ink": 500},
      [E("throughput", W("gatherer"), 1.8)], "Gatherers make almost twice as much ink.", revealAt={"ink": 300}),
    U("barge", "A barge to the cathedral", "Downstream in a day, upstream in three.", 2, {"pennies": 12000},
      [E("throughput", W("pedlar"), 2), E("yield", W("miller"), 1.3)], "Pages and ink travel farther and sell better.", revealAt={"pennies": 7000}),

    # Chapter three: The Bindery
    U("oak_boards", "Oak boards", "Quarter-sawn, so they do not warp on a damp shelf.", 3, {"pennies": 20000},
      [E("throughput", W("binder"), 1.5)], "Binders work half as fast again.", revealAt={"books": 2}),
    U("clasps", "Brass clasps", "A book that shuts tight keeps its shape for a century.", 3, {"pennies": 35000, "books": 5},
      [E("yield", W("bookseller"), 1.4)], "Books fetch higher prices.", revealAt={"books": 5}),
    U("sewing_frame", "A sewing frame", "Five cords, five stations, one needle that never stops.", 3, {"pennies": 45000},
      [E("throughput", A("sew"), 3), E("throughput", W("binder"), 1.3)], "Sew three gatherings at a time.", revealAt={"pennies": 25000}),
    U("glue_pot", "A glue pot on the hob", "Hide glue, kept warm from matins to vespers.", 3, {"pennies": 70000},
      [E("thrift", W("binder"), 0.8)], "Binders spoil fewer folios.", revealAt={"pennies": 40000}),
    U("shop_sign", "A painted sign", "An inkhorn and a quill, gilded, hung over the lane.", 3, {"pennies": 90000},
      [E("throughput", W("bookseller"), 1.6)], "Booksellers sell more books.", revealAt={"pennies": 55000}),
    U("apprentices", "Indentured apprentices", "Seven years' board for seven years' work.", 3, {"pennies": 120000, "books": 10},
      [E("cost", W("binder"), 0.7), E("throughput", W("scrivener"), 1.5)], "Binders cost less to hire; scriveners copy more.", revealAt={"books": 10}),
    U("catalogue", "A written catalogue", "Every title, its price and its binding, copied fair for each customer.", 3, {"pennies": 160000, "ink": 3000},
      [E("yield", W("bookseller"), 1.5)], "Customers buy the dearer bindings.", revealAt={"books": 15}),
    U("cathedral_school", "The cathedral school's list", "Donatus, Cato, the psalter, and the Doctrinale for older boys.", 3, {"pennies": 220000},
      [E("throughput", W("binder"), 1.8), E("throughput", W("miller"), 1.5)], "Binders and the mill both work harder.", revealAt={"books": 20}),

    # Chapter four: The Lending Library
    U("borrowers_roll", "A borrowers' roll", "Name, book, pledge and date, in a hand anyone can read.", 4, {"pennies": 300000},
      [E("throughput", W("librarian"), 1.5)], "Librarians lend more books.", revealAt={"renown": 20}),
    U("chains", "Lighter chains", "Long enough to carry a book to the window.", 4, {"pennies": 450000, "books": 20},
      [E("thrift", W("librarian"), 0.5)], "Books wear out half as fast.", revealAt={"renown": 60}),
    U("candles", "Beeswax candles", "They cost four times as much as tallow and do not stink.", 4, {"pennies": 600000, "renown": 100},
      [E("throughput", W("lector"), 1.5), E("yield", A("read"), 2)], "Evening lessons run later.", revealAt={"renown": 120}),
    U("benches", "Benches in the lane", "Built by the wheelwright, who wants his sons taught.", 4, {"pennies": 800000},
      [E("yield", W("librarian"), 1.5)], "Borrowers bring back more renown.", revealAt={"renown": 250}),
    U("horn_books", "Horn books", "An alphabet under a sheet of horn, on a paddle a child can hold.", 4, {"pennies": 1000000, "ink": 20000},
      [E("yield", W("lector"), 1.6)], "Pupils learn faster.", revealAt={"renown": 400}),
    U("reading_room", "A reading room", "The granary above the shop, floored and glazed.", 4, {"pennies": 1500000, "renown": 600},
      [E("throughput", W("librarian"), 1.8), E("throughput", W("bookseller"), 1.4)], "More readers, more buyers.", revealAt={"renown": 600}),
    U("fair_copies", "Fair copies for loan", "Plain books, meant to be read to pieces.", 4, {"pennies": 2000000, "books": 60},
      [E("throughput", W("binder"), 1.6), E("throughput", W("scrivener"), 1.4)], "The bindery and the desks work faster.", revealAt={"renown": 900}),
    U("guild_hall", "Lessons at the guild hall", "The drapers want their apprentices to reckon and read.", 4, {"pennies": 3000000, "renown": 1200},
      [E("throughput", W("lector"), 1.8)], "Lectors teach more pupils.", revealAt={"renown": 1300}),

    # Chapter five: The Press
    U("rag_merchant", "A rag merchant", "Old linen from three counties, sorted by colour.", 5, {"pennies": 2500000},
      [E("throughput", W("papermaker"), 1.4)], "Papermakers never wait for rags.", revealAt={"paper": 10}),
    U("linseed_ink", "Linseed printing ink", "Lampblack and boiled oil. Pieter will not say how long he boils it.", 5, {"pennies": 3500000, "ink": 40000},
      [E("thrift", W("pressman"), 0.7), E("yield", A("pull"), 1.5)], "Presses use less paper and ink.", revealAt={"type": 5}),
    U("hard_metal", "Hard type metal", "Lead, tin and antimony, which Pieter calls his secret.", 5, {"pennies": 5000000},
      [E("throughput", W("founder"), 1.7)], "Founders cast more type.", revealAt={"type": 30}),
    U("second_press", "A second press", "Built by the shipwright from Pieter's drawing.", 5, {"pennies": 8000000, "renown": 2000},
      [E("throughput", W("pressman"), 1.5)], "Pressmen print half as much again.", revealAt={"folios": 3000}),
    U("folding_table", "A folding table", "Long enough for four boys to fold a whole edition.", 5, {"pennies": 6000000},
      [E("throughput", W("binder"), 2)], "Binders keep up with the press.", revealAt={"folios": 2000}),
    U("vatman", "A master vatman", "He can lay a sheet as thin as a leaf and as even as a pond.", 5, {"pennies": 10000000},
      [E("throughput", W("papermaker"), 1.8)], "Papermakers make more paper.", revealAt={"paper": 400}),
    U("title_pages", "Printed title pages", "The book says who made it, and where to buy another.", 5, {"pennies": 14000000, "books": 400},
      [E("yield", W("bookseller"), 1.8)], "Books sell for more.", revealAt={"books": 300}),
    U("night_shift", "Lamps for a night shift", "The press never cools now.", 5, {"pennies": 20000000, "renown": 5000},
      [E("throughput", W("pressman"), 1.6), E("throughput", W("founder"), 1.3)], "The press runs through the night.", revealAt={"books": 800}),

    # Chapter six: The Printing City
    U("river_wharf", "A wharf on the Sele", "Stone steps down to the water, and a crane.", 6, {"pennies": 40000000},
      [E("throughput", W("carter"), 1.5)], "Carters move more books.", revealAt={"books": 100}),
    U("fair_privilege", "A fair privilege", "Your books sold at every chartered fair without toll.", 6, {"pennies": 60000000, "renown": 20000},
      [E("yield", W("carter"), 1.5)], "Carters earn more for each book.", revealAt={"renown": 1000}),
    U("type_foundry", "A type foundry", "Six casters, and a furnace that is never let out.", 6, {"pennies": 80000000},
      [E("throughput", W("founder"), 2), E("throughput", W("pressman"), 1.3)], "More type, more presses.", revealAt={"pennies": 50000000}),
    U("paper_mill", "A paper mill at the weir", "The wheel Aldous built for ink turns the stampers now.", 6, {"pennies": 90000000},
      [E("throughput", W("papermaker"), 2.2)], "Paper is plentiful.", revealAt={"paper": 5000}),
    U("almanacs", "Almanacs", "Saints' days, moons and fairs for the coming year. Everyone buys one.", 6, {"pennies": 120000000, "books": 3000},
      [E("yield", W("carter"), 1.6), E("yield", A("cry"), 2)], "Cheap books sell everywhere.", revealAt={"books": 2000}),
    U("bindery_hall", "A bindery hall", "Forty binders under one roof, and a foreman who shouts.", 6, {"pennies": 150000000},
      [E("throughput", W("binder"), 2.5)], "Binders keep pace with the city's presses.", revealAt={"pennies": 90000000}),
    U("schoolrooms", "Schoolrooms in every parish", "Readers make buyers.", 6, {"pennies": 200000000, "renown": 60000},
      [E("throughput", W("lector"), 2), E("yield", W("librarian"), 2)], "Renown grows across the city.", revealAt={"renown": 40000}),
    U("roads", "The mended roads", "Paid for by the printing houses, for once, instead of the tolls.", 6, {"pennies": 300000000, "renown": 100000},
      [E("throughput", W("carter"), 2)], "Carters reach every town on the Sele.", revealAt={"renown": 80000}),
]

chapters = [
    {"number": 1, "title": "The Quill", "initial": "O", "work": "copying",
     "opening": "One candle, one quill, three skins of vellum, and a letter from the abbot: he wants a psalter by Lent.",
     "goalText": "Copy the abbot's psalter", "goalResource": "folios", "goalAmount": 200,
     "triggers": [{"choiceId": "herbal", "at": 0.4}, {"choiceId": "tithe", "at": 0.8}],
     "endLine": "The psalter goes up to the abbey wrapped in a clean cloth. The abbot turns every page and pays in silver.",
     "noteLine": "The psalter is finished. Chapter two waits in the ledger."},
    {"number": 2, "title": "The Ink Mill", "initial": "T", "work": "grinding",
     "opening": "The ink runs short before the vellum does now, and old Aldous has a mill on the Sele that grinds nothing but barley.",
     "goalText": "Grind a winter's ink for the diocese", "goalResource": "ink", "goalAmount": 8500,
     "triggers": [{"choiceId": "river", "at": 0.4}, {"choiceId": "galls", "at": 0.8}],
     "endLine": "The last cask of ink goes down the river to the cathedral, sealed with wax and your mark.",
     "noteLine": "The ink mill is finished. Chapter three waits in the ledger."},
    {"number": 3, "title": "The Bindery", "initial": "F", "work": "binding",
     "opening": "Folios lie in loose drifts on every shelf, and the cathedral school wants books, sewn and boarded, by Michaelmas.",
     "goalText": "Bind books for the cathedral school", "goalResource": "books", "goalAmount": 2300,
     "triggers": [{"choiceId": "chains", "at": 0.4}, {"choiceId": "seal", "at": 0.8}],
     "endLine": "The school's books leave in nine carts, a ton of oak and vellum, and the schoolmaster weeps a little.",
     "noteLine": "The bindery is finished. Chapter four waits in the ledger."},
    {"number": 4, "title": "The Lending Library", "initial": "B", "work": "lending",
     "opening": "Books come back to the shop with thumbprints and questions, and people start to ask whether they might borrow one.",
     "goalText": "Earn the town's trust", "goalResource": "renown", "goalAmount": 90000,
     "triggers": [{"choiceId": "borrow", "at": 0.4}, {"choiceId": "commentary", "at": 0.8}],
     "endLine": "The borrowers' roll runs to nine hundred names. Half of them signed it themselves.",
     "noteLine": "The lending library is finished. Chapter five waits in the ledger."},
    {"number": 5, "title": "The Press", "initial": "A", "work": "printing",
     "opening": "A crate comes up the river from Mainz: a screw press in pieces, a case of type, and a printer named Pieter who speaks no English.",
     "goalText": "Print the first edition", "goalResource": "books", "goalAmount": 46000,
     "triggers": [{"choiceId": "strike", "at": 0.4}, {"choiceId": "patent", "at": 0.8}],
     "endLine": "Forty thousand copies of one book, every page the same. Pieter gets drunk for the first time anyone has seen.",
     "noteLine": "The first edition is printed. Chapter six waits in the ledger."},
    {"number": 6, "title": "The Printing City", "initial": "H", "work": "printing",
     "opening": "Hollinford has seven presses now, and every one of them is yours or owes you money.",
     "goalText": "Carry books to every town on the Sele", "goalResource": "renown", "goalAmount": 950000,
     "triggers": [{"choiceId": "edition", "at": 0.6}],
     "endLine": "The last cart comes home empty from the last town on the river.",
     "noteLine": "The printing city is finished. The last page waits in the ledger."},
]

def O(id, label, consequence, leaning, effects, line):
    return {"id": id, "label": label, "consequence": consequence, "leaning": leaning, "effects": effects, "line": line}

choices = [
    {"id": "herbal", "chapter": 1, "title": "The forbidden herbal",
     "openLine": "Brother Osric brings a herbal the bishop has forbidden.",
     "passage": "Brother Osric comes after compline with something under his habit. It is a herbal, forty leaves of it, with drawings of feverfew and henbane and a recipe for bringing down a fever that the bishop has called witchcraft. \"The women in the lanes use it anyway,\" he says, \"and they use it wrong. A good copy would save lives.\" He does not say whose lives, or what the bishop does to scribes. The candle gutters. You have vellum enough for one book tonight, and it could be this one.",
     "options": [
         O("candle", "Copy it by candlelight", "Pages of remedies sell well in the lanes: pedlars earn more, and the abbey starts to watch you.", "commons",
           [E("yield", W("pedlar"), 1.3), E("throughput", W("novice"), 0.9)],
           "You copied the herbal by candlelight. The pages went out to the lanes folded inside psalms."),
         O("burn", "Burn it in the abbey kitchen", "The abbot sees you do it. Novices are sent to you gladly and copy harder.", "cloister",
           [E("throughput", W("novice"), 1.3), E("yield", W("pedlar"), 0.9)],
           "You burned the herbal in the abbey kitchen. Brother Osric did not speak to you for a month."),
         O("bishop", "Send it to the bishop's court", "The bishop's clerk rewards the report: vellum comes cheaper, and the lanes remember who told.", "crown",
           [E("yield", W("tanner"), 1.3), E("yield", A("sell"), 0.9)],
           "You sent the herbal to the bishop's court. A clerk wrote your name in a book of loyal men."),
     ]},
    {"id": "tithe", "chapter": 1, "title": "The abbot's tithe",
     "openLine": "The abbot asks a tithe of every tenth folio.",
     "passage": "The psalter is nearly done when the cellarer arrives with a wax tablet and a smile. The abbey, he explains, has always had a tenth of the work done within sight of its bells. Your shop is within sight of its bells. He would take the tenth in folios, freshly written, for the abbey's own library; or in coin, if you prefer, at a price he names and you think unkind. Across the lane, the sheriff's man is listening with interest. He says the abbey's rights stop at the abbey wall.",
     "options": [
         O("pay", "Give the tenth gladly", "Monks sit with your scriveners in the long evenings and teach them the old hands.", "cloister",
           [E("throughput", W("scrivener"), 1.25), E("throughput", W("pedlar"), 0.9)],
           "You gave the abbey its tenth. The cellarer blessed the desks, and the scriveners learned the old hands."),
         O("sheriff", "Ask the sheriff to settle it", "The sheriff rules for you, for a fee. Hiring is cheaper; the abbot's novices come less willingly.", "crown",
           [E("cost", W("scrivener"), 0.8), E("throughput", W("novice"), 0.95)],
           "You asked the sheriff to settle it. He ruled for you and dined at your table for a year."),
         O("refuse", "Refuse, and sell the tenth in the square", "The town cheers a scribe who defies the abbey. Pages sell faster; vellum costs more.", "commons",
           [E("throughput", W("pedlar"), 1.3), E("yield", W("tanner"), 0.9)],
           "You refused the tithe and sold the tenth in the square. The alewives drank your health."),
     ]},
    {"id": "river", "chapter": 2, "title": "The river rights",
     "openLine": "The fishers and the mill both claim the water of the Sele.",
     "passage": "The new millrace takes water the fishers have always had. By the end of the first week there are no eels at the weir, and forty men stand outside the mill with nets over their shoulders. Aldous wants you to buy the water outright: the Crown sells charters to anyone with the coin. The prior says the weir was the abbey's before there was a town, and would lease it to you for prayers and a little silver. The fishers only want the sluice shut on Fridays.",
     "options": [
         O("share", "Shut the sluice on Fridays", "The fishers carry your ink to market for nothing. The mill grinds six days in seven.", "commons",
           [E("throughput", W("miller"), 0.85), E("throughput", W("gatherer"), 1.4)],
           "You shut the sluice on Fridays. The fishers left eels on your step for a month."),
         O("charter", "Buy the charter from the Crown", "The water is yours alone. The mill grinds hard; the lanes stop gathering for you.", "crown",
           [E("throughput", W("miller"), 1.35), E("throughput", W("gatherer"), 0.85)],
           "You bought the river charter from the Crown. It hangs framed in the mill, and the fishers spit when they pass."),
         O("lease", "Lease the weir from the abbey", "The abbey's lay brothers work the mill for a share. Ink is steady, and the abbey takes its cut.", "cloister",
           [E("throughput", W("miller"), 1.15), E("yield", W("miller"), 0.85), E("throughput", W("scrivener"), 1.2)],
           "You leased the weir from the abbey. Lay brothers work the sluice and pray at the wheel."),
     ]},
    {"id": "galls", "chapter": 2, "title": "The oak-gall harvest",
     "openLine": "The best oak galls grow in the king's forest.",
     "passage": "The galls this autumn are small and pale, except in the king's forest across the river, where the old oaks are thick with them. The verderer will sell a licence to gather there, dear, and send a man to count every basket. The common wood is free, but the galls there are poor and the children who gather them come home scratched. The abbey has an orchard of young oaks behind the infirmary, and the infirmarian says the trees would thank you for the pruning.",
     "options": [
         O("common", "Gather in the common wood", "Poor galls but free. Gatherers are cheap to hire; hand-ground ink is weaker.", "commons",
           [E("cost", W("gatherer"), 0.7), E("yield", A("grind"), 0.8)],
           "You gathered in the common wood. The children came home scratched and paid."),
         O("licence", "Buy the verderer's licence", "Black, heavy galls. Gatherers make more ink and cost more to hire.", "crown",
           [E("yield", W("gatherer"), 1.4), E("cost", W("gatherer"), 1.15)],
           "You bought the verderer's licence. The ink that winter was the blackest in the diocese."),
         O("orchard", "Prune the abbey orchard", "A modest harvest, and the infirmarian's gratitude: novices are lent to the mill.", "cloister",
           [E("throughput", W("novice"), 1.5), E("yield", W("gatherer"), 1.1)],
           "You pruned the abbey orchard. The infirmarian sent novices to help at the mill."),
     ]},
    {"id": "chains", "chapter": 3, "title": "The chained books",
     "openLine": "The canons want every book chained to its desk.",
     "passage": "A book costs as much as a cow now, and the canons of the cathedral want theirs chained to the desks: iron staples through the boards, a chain to a rod, the rod locked. It is how books have always been kept. But the schoolmaster wants his boys to take books home to their mothers' tables, and the lord of the manor wants to buy a shelf of them outright for a hall where nobody reads. Every binding you make this month must be one kind or the other.",
     "options": [
         O("leave", "Let them leave the house", "Books go home with readers. More come back worn, but more people want them.", "commons",
           [E("throughput", W("bookseller"), 1.3), E("thrift", W("binder"), 1.1)],
           "You let the books leave the house. Half came back with jam on them, and twice as many people asked for one."),
         O("chain", "Chain them for the canons", "Chained books last. Binders work slower on the ironwork; each book is worth more.", "cloister",
           [E("yield", W("bookseller"), 1.3), E("throughput", W("binder"), 0.9)],
           "You chained the books for the canons. The cathedral library rang like a smithy."),
         O("manor", "Sell them to the manor houses", "Gentlemen pay for gilt edges and never open them. Money, and little else.", "crown",
           [E("yield", W("bookseller"), 1.45), E("throughput", W("bookseller"), 0.85)],
           "You sold the books to the manor houses. They are very beautiful and very clean."),
     ]},
    {"id": "seal", "chapter": 3, "title": "The guild seal",
     "openLine": "The Stationers' guild offers you its seal.",
     "passage": "Master Ranulf of the Stationers comes in his best gown to offer you the guild's seal. With it, your bindings may be sold in any guild town; without it, they may be seized at the gate. The price is the guild's rules: fixed prices, a fixed number of apprentices, and a fine for any book sold on a Sunday. The king's chancellor, meanwhile, grants warrants to favoured shops that put them above any guild. And the abbey will let you bind under its own ancient mark, which no guild has ever dared to touch.",
     "options": [
         O("guild", "Take the guild's seal", "Fair prices and a fixed trade. Apprentices are cheaper; booksellers earn less on each book.", "commons",
           [E("cost", W("binder"), 0.75), E("yield", W("bookseller"), 0.9), E("throughput", W("binder"), 1.2)],
           "You took the Stationers' seal. Master Ranulf kissed you on both cheeks."),
         O("warrant", "Hold out for a royal warrant", "Above the guild and its rules. Books sell dear, and the guild will not forget.", "crown",
           [E("yield", W("bookseller"), 1.35), E("cost", W("binder"), 1.15)],
           "You held out for a royal warrant. It came in a year, with a seal the size of a saucer."),
         O("abbey_mark", "Bind under the abbey's mark", "Protected and quiet. Monks bind beside your binders; fewer books reach the market.", "cloister",
           [E("throughput", W("binder"), 1.35), E("throughput", W("bookseller"), 0.9)],
           "You bound under the abbey's mark. No guildsman has ever set foot in the bindery."),
     ]},
    {"id": "borrow", "chapter": 4, "title": "Who may borrow",
     "openLine": "The lending room needs a rule for who may borrow.",
     "passage": "The lending room has forty books and a hundred people who want them. A book lent is a book that might not come back. Margery says anyone who can sign their name in the roll should have one for a week, and that the ones who cannot sign will soon learn. The mayor says books should go to men of standing, who can be made to pay for a loss. The prior says the room should serve the clergy and their students, as libraries always have, and that he will pay for the chains.",
     "options": [
         O("anyone", "Anyone who can sign their name", "The whole lane comes to borrow. Renown spreads, and books wear out.", "commons",
           [E("throughput", W("librarian"), 1.4), E("thrift", W("librarian"), 1.5)],
           "You lent to anyone who could sign. By Easter, forty people had learned to write their names."),
         O("standing", "Men of standing and their households", "Fewer borrowers, all of them able to pay. Pennies come in; renown is slower.", "crown",
           [E("yield", W("bookseller"), 1.3), E("throughput", W("librarian"), 0.85)],
           "You lent to men of standing. The mayor's wife returned every book with a note of thanks."),
         O("clergy", "Clerics and their students", "Careful readers. Books last, and the abbey pays for lessons.", "cloister",
           [E("thrift", W("librarian"), 0.5), E("yield", W("lector"), 1.25)],
           "You lent to clerics and their students. The roll is short and written in Latin."),
     ]},
    {"id": "commentary", "chapter": 4, "title": "The heretic's commentary",
     "openLine": "A preacher leaves a commentary on the Gospels in English.",
     "passage": "A travelling preacher sleeps a night in the reading room and is gone before dawn, leaving a book behind. It is a commentary on the Gospels in plain English, well argued and fiercely against the bishops. Men have been burned for owning less. Margery has already read half of it and wants it shelved with the rest. The king's justices are in the county for the assizes and would reward whoever brought it to them. The abbot has an aumbry with a good lock, and asks no questions about what goes into it.",
     "options": [
         O("shelve", "Shelve it with the rest", "Word gets round. The reading room fills; a bishop's man starts to call.", "commons",
           [E("throughput", W("lector"), 1.35), E("yield", W("bookseller"), 0.85)],
           "You shelved the commentary with the rest. It was borrowed three hundred times, and never by the same hand."),
         O("justices", "Hand it to the king's justices", "The Crown rewards loyalty with trade. Readers stop trusting the room.", "crown",
           [E("yield", W("bookseller"), 1.35), E("throughput", W("lector"), 0.85)],
           "You handed the commentary to the king's justices. The preacher was taken at Ludlow."),
         O("aumbry", "Lock it in the abbot's aumbry", "Safe and unread. The abbot's gratitude runs to candles and teachers.", "cloister",
           [E("throughput", W("librarian"), 1.3), E("cost", W("lector"), 0.8)],
           "You locked the commentary in the abbot's aumbry. Nobody knows if he read it."),
     ]},
    {"id": "strike", "chapter": 5, "title": "The scribes' strike",
     "openLine": "The scribes put down their pens and refuse to work beside the press.",
     "passage": "On a Monday morning every desk in the scriptorium is empty. The scribes stand in the lane with their pens behind their ears. They have seen what the press does: in a day it makes what a scrivener makes in a year, and it does not need a scrivener to do it. Some of them have worked for you since the psalter. The sheriff offers to clear the lane. Pieter says, through Margery, that a good scribe would make a good compositor. The abbey says it will take in any scribe who wants a cell.",
     "options": [
         O("teach", "Teach them to set type", "Slow at first. Scribes become compositors, and the press never lacks hands.", "commons",
           [E("throughput", W("founder"), 1.3), E("throughput", W("pressman"), 1.2), E("throughput", W("scrivener"), 0.8)],
           "You taught the scribes to set type. Old Wat set his first line upside down, and his hundredth perfectly."),
         O("break", "Break the strike with the sheriff", "The lane is cleared. Cheap hands come in from outside; the town remembers.", "crown",
           [E("cost", W("pressman"), 0.8), E("throughput", W("lector"), 0.85)],
           "You broke the strike with the sheriff. The lane was cleared by noon."),
         O("cells", "Let the abbey take them in", "The scribes go to the cloister and keep copying there. The abbey's books pass through your bindery.", "cloister",
           [E("throughput", W("binder"), 1.3), E("throughput", W("papermaker"), 0.9)],
           "You let the abbey take the scribes in. They copy psalters there still, very slowly and very well."),
     ]},
    {"id": "patent", "chapter": 5, "title": "The royal patent",
     "openLine": "The king offers a patent: the sole right to print in the shire.",
     "passage": "The king's messenger reads the patent aloud in the market so that everyone hears. It grants the printer at the sign of the Inkhorn the sole right to print books in the shire for twenty years, and makes any other press a crime. It asks, in return, that nothing be printed that the king's council has not seen. Pieter goes white. Margery says a monopoly is a cage with a gold floor. The abbot suggests, gently, that the patent could as easily be made out to the abbey.",
     "options": [
         O("refuse_patent", "Refuse it, and print for anyone", "Rival presses open in the county. Your books are cheap and everywhere.", "commons",
           [E("throughput", W("bookseller"), 1.4), E("yield", W("bookseller"), 0.85)],
           "You refused the patent and printed for anyone who paid. Three new presses opened by Whitsun."),
         O("accept", "Accept the patent", "No rivals, and the council reads every page first. Prices rise.", "crown",
           [E("yield", W("bookseller"), 1.3), E("throughput", W("lector"), 0.8)],
           "You accepted the royal patent. The council's reader sits in the corner of the composing room."),
         O("abbey_patent", "Ask for the patent in the abbey's name", "The abbey holds the right and licenses you. Steady, pious and protected.", "cloister",
           [E("throughput", W("pressman"), 1.2), E("yield", W("bookseller"), 0.9)],
           "You asked for the patent in the abbey's name. The abbot holds it, and prints nothing he has not prayed over."),
     ]},
    {"id": "edition", "chapter": 6, "title": "The last edition",
     "openLine": "The presses are ready for one great last edition.",
     "passage": "Pieter is old now, and so are you. Hollinford is a city of paper and lead, and the presses can make anything. There is money and paper enough for one great edition, the biggest ever pulled on the Sele, and it will be remembered as what the Inkhorn was for. Margery has written a primer: letters, sums, and the laws every tenant should know, to be sold for the price of a loaf. The king wants his statutes, with his arms on the title page. The abbot wants a Bible, a great one, printed once and never again. This choice counts twice.",
     "weight": 2,
     "options": [
         O("primer", "A primer for every parish, sold at cost", "Carters take it everywhere. Little profit, and every town knows your name.", "commons",
           [E("yield", W("carter"), 0.8), E("throughput", W("carter"), 1.3)],
           "You printed the primer for every parish. Sixty thousand copies, sold for the price of a loaf."),
         O("statutes", "The king's statutes, under his arms", "The Crown buys the whole edition and pays in gold.", "crown",
           [E("yield", W("carter"), 1.3), E("throughput", W("carter"), 0.85)],
           "You printed the king's statutes under his arms. A copy is chained in every court in the realm."),
         O("bible", "A great Bible for the abbey", "One vast edition, slowly made. The abbey sends every lay brother to help.", "cloister",
           [E("throughput", W("binder"), 1.3), E("throughput", W("pressman"), 1.1), E("yield", W("carter"), 0.95)],
           "You printed the abbey's great Bible. It took two years, and the abbey rang its bells for a day."),
     ]},
]

endings = [
    {"id": "open_stacks", "leaning": "commons", "title": "The Open Stacks",
     "passage": [
         "They pulled the chains out of the lending room the winter Pieter died, and nobody ever put them back.",
         "Hollinford became a town where carters read the notices they carried and fishwives argued about Boethius. Your cheapest books went through eleven editions. Children learned their letters from it at the kitchen table, then taught their parents, who were embarrassed and grateful in equal measure. Rival presses opened in every town on the Sele, and most of them were set up by men and women who had borrowed their first book from your shelves.",
         "You never became rich. The bishops never forgave you for putting books in every kitchen, and the king's council kept a file with your name on it that grew thicker every year. But when you were too old to pull a press, people still came to the door of the Inkhorn to ask for a book, and you still lent it to them.",
         "When you died, the town put your name over the lending room. It is still open. Anyone who can sign their name may borrow a book for a week.",
     ]},
    {"id": "kings_printer", "leaning": "crown", "title": "The King's Printer",
     "passage": [
         "The Crown's work came to you year after year, and at last the king made you his printer by name.",
         "The Inkhorn printed the statutes of the realm, the proclamations of three reigns, the prayer book the council approved and the almanac the council corrected. Your carters wore the royal livery. Your house on the wharf had glass in every window and a cook from Bruges. When the other printers of the county were closed down, one by one, you bought their type at auction and melted it for your own.",
         "Every page that left Hollinford was read first by a council reader who sat in the corner of the composing room, and he was a careful reader. Nothing foolish was printed, and nothing dangerous, and very little that was new.",
         "You were buried in the cathedral, under a brass with your arms on it and a quill in a gloved hand. The Inkhorn still prints for the Crown. It has never missed a deadline, and never once been surprised.",
     ]},
    {"id": "quiet_cloister", "leaning": "cloister", "title": "The Quiet Cloister",
     "passage": [
         "In the end you gave the presses to the abbey, and walked up the hill after them.",
         "The monks print slowly. They print psalters and missals and lives of the saints, and books that take a year each and are chained in the nave where the light falls on them at noon. The old scribes copy beside the press still, in a warm room, very slowly and very well. The town below grew quieter as the trade moved downriver to louder places.",
         "You took the habit at sixty. You rise for matins and correct proofs by candlelight, as you once copied the abbot's psalter, and you teach the novices to cut a goose quill from the left wing.",
         "When you died they buried you in the cloister garth. The abbey keeps a chronicle of everything it has printed, in your hand until the last page, and in a novice's hand after that.",
     ]},
]

credits = [
    "Inkhorn",
    "Written, designed and built by SocialSure Private Limited.",
    "Display type: Grenze Gotisch by Omnibus-Type, under the SIL Open Font License 1.1.",
    "Text type: Alegreya by Huerta Tipografica, under the SIL Open Font License 1.1.",
    "Hollinford, the Sele and everyone in them are invented. The book trade is remembered loosely and with affection.",
    "Thank you for reading to the last page.",
]

def walk(o):
    if isinstance(o, dict):
        return {k: (smart(v) if isinstance(v, str) and k in PROSE else walk(v)) for k, v in o.items()}
    if isinstance(o, list):
        return [walk(x) for x in o]
    return o

PROSE = {"boughtLine", "firstHireLine", "passage", "consequence", "line", "openLine", "opening", "endLine", "noteLine", "revealLine", "doneLine", "about",
         "period", "effectText", "goalText", "label", "title", "name", "one", "many"}

import story_more
for ch in chapters:
    ch["beats"] = [{"at": at, "text": t} for at, t in story_more.BEATS[ch["number"]]]
for u in upgrades:
    u["boughtLine"] = story_more.BOUGHT[u["id"]]
for w in workers:
    w["firstHireLine"] = story_more.FIRST_HIRE[w["id"]]
for e in endings:
    e["passage"] = e["passage"][:-1] + story_more.ENDING_MORE[e["id"]] + e["passage"][-1:]

story = {
    "engineVersion": 1,
    "start": {"vellum": 3},
    "resources": resources, "actions": actions, "workers": workers, "upgrades": upgrades,
    "chapters": chapters, "choices": choices, "endings": endings, "credits": credits,
}
_goals = __import__("os").environ.get("GOALS")
if _goals:
    for c, g in zip(chapters, _goals.split(",")):
        c["goalAmount"] = float(g)
story = walk(story)
# ending paragraphs and credits are plain string lists
story["endings"] = [dict(e, passage=[smart(p) for p in e["passage"]]) for e in story["endings"]]
story["credits"] = [smart(c) for c in story["credits"]]

text = json.dumps(story, ensure_ascii=False, indent=1)
assert "\u2014" not in text and "\u2013" not in text
import os
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "w").write(text)
words = len(re.findall(r"[A-Za-z’']+", " ".join(re.findall(r'"(?:passage|consequence|line|openLine|opening|endLine|revealLine|period)": "([^"]*)"', text)) + " ".join(p for e in endings for p in e["passage"])))
print("wrote", OUT, len(text), "bytes;", len(upgrades), "upgrades;", len(workers), "workers;", len(choices), "choices; ~words", words)
