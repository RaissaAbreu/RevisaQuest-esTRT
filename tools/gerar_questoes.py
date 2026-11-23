#!/usr/bin/env python3
"""Lê os PDFs de questões Certo/Errado em dados/pdfs/ e gera a base do app.

Saída: app/src/main/assets/questoes.json

Formato esperado de cada PDF (o mesmo dos "500 questões Certo ou Errado"):
  - 2ª linha: "<Matéria> · ..."
  - títulos "Incidência Altíssima|Alta|Média|Baixa"
  - títulos "Ponto N. <assunto>" (podem ocupar mais de uma linha)
  - itens "N. (   ) <enunciado>"
  - "Gabarito rápido" com "N C" / "N E"
  - "Gabarito comentado" com "N. CERTO|ERRADO — <comentário>"

Uso: python3 tools/gerar_questoes.py
Requer o pdftotext (pacote poppler-utils).
"""
import hashlib
import json
import re
import subprocess
import sys
import unicodedata
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
PASTA_PDFS = RAIZ / "dados" / "pdfs"
SAIDA = RAIZ / "app" / "src" / "main" / "assets" / "questoes.json"

INCIDENCIAS = {"Altíssima": 4, "Alta": 3, "Média": 2, "Baixa": 1}

# Ordem das matérias no app (as que não estiverem aqui vão para o fim, em ordem alfabética)
ORDEM_MATERIAS = [
    "Direito do Trabalho",
    "Direito Processual do Trabalho",
    "Direito Constitucional",
    "Direito Administrativo",
    "Direito Civil",
    "Direito Processual Civil",
    "Direitos Humanos",
]

RE_RODAPE = re.compile(r"^\s*\d+\s+questões C/E\s+·.*·\s*\d+\s*$")
RE_INCID = re.compile(r"^\s*Incidência (Altíssima|Alta|Média|Baixa)\s*$")
RE_PONTO = re.compile(r"^\s*Ponto (\d+)\.\s*(.*)$")
RE_ITEM = re.compile(r"^\s*(\d+)\.\s*\(\s*\)\s*(.*)$")
RE_COMENT = re.compile(r"^\s*(\d+)\.\s*(CERTO|ERRADO)\s*[—–-]\s*(.*)$")
RE_RAPIDO = re.compile(r"(\d+)\s*([CE])\b")


def slug(s: str) -> str:
    s = unicodedata.normalize("NFD", s).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "_", s).strip("_")


def normalizar(s: str) -> str:
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn").lower()
    return re.sub(r"\s+", " ", s).strip()


def juntar(linhas):
    """Junta linhas quebradas pelo PDF em um parágrafo."""
    texto = ""
    for l in linhas:
        l = l.strip()
        if not l:
            continue
        if not texto:
            texto = l
        elif texto.endswith("-") and len(texto) > 1 and texto[-2].isalpha():
            # quebra em palavra composta ("sub-" + "rogados"): mantém o hífen, sem espaço
            texto += l
        else:
            texto += " " + l
    return texto


def ler_pdf(caminho: Path) -> str:
    r = subprocess.run(["pdftotext", "-layout", str(caminho), "-"], capture_output=True, text=True, check=True)
    return r.stdout


class ErroFormato(Exception):
    pass


def analisar(caminho: Path):
    linhas = [l.replace("\f", "") for l in ler_pdf(caminho).splitlines()]
    linhas = [l for l in linhas if not RE_RODAPE.match(l)]

    nao_vazias = [l.strip() for l in linhas if l.strip()]
    if len(nao_vazias) < 2:
        raise ErroFormato("PDF sem texto")
    materia = nao_vazias[1].split("·")[0].strip()

    try:
        i_rapido = next(i for i, l in enumerate(linhas) if l.strip() == "Gabarito rápido")
        i_coment = next(i for i, l in enumerate(linhas) if l.strip() == "Gabarito comentado")
    except StopIteration:
        raise ErroFormato("não encontrei 'Gabarito rápido' / 'Gabarito comentado'")

    # --- questões ---
    questoes = {}
    pontos = {}
    incid = None
    atual = None  # ("item", numero, [linhas]) ou ("ponto", numero, [linhas])
    ponto_atual = None
    comecou = False

    def fechar():
        nonlocal atual
        if atual is None:
            return
        tipo, n, ls = atual
        if tipo == "item":
            questoes[n]["enunciado"] = juntar(ls)
        else:
            pontos[n] = juntar(ls)
        atual = None

    for l in linhas[:i_rapido]:
        m = RE_INCID.match(l)
        if m:
            fechar()
            incid = INCIDENCIAS[m.group(1)]
            comecou = True
            continue
        if not comecou:
            continue
        m = RE_PONTO.match(l)
        if m:
            fechar()
            ponto_atual = int(m.group(1))
            atual = ("ponto", ponto_atual, [m.group(2)])
            continue
        m = RE_ITEM.match(l)
        if m:
            fechar()
            n = int(m.group(1))
            if n in questoes:
                raise ErroFormato(f"questão {n} repetida")
            if incid is None or ponto_atual is None:
                raise ErroFormato(f"questão {n} sem incidência ou ponto")
            questoes[n] = {"numero": n, "incidencia": incid, "ponto": ponto_atual}
            atual = ("item", n, [m.group(2)])
            continue
        if atual is not None:
            if l.strip():
                atual[2].append(l)
            elif atual[0] == "ponto":
                fechar()

    fechar()

    # --- gabarito rápido ---
    rapido = {}
    for l in linhas[i_rapido + 1:i_coment]:
        for n, g in RE_RAPIDO.findall(l):
            rapido[int(n)] = g == "C"

    # --- gabarito comentado ---
    coment = {}
    gab = {}
    atual_c = None
    for l in linhas[i_coment + 1:]:
        if RE_PONTO.match(l):
            atual_c = None
            continue
        m = RE_COMENT.match(l)
        if m:
            atual_c = int(m.group(1))
            gab[atual_c] = m.group(2) == "CERTO"
            coment[atual_c] = [m.group(3)]
            continue
        if atual_c is not None and l.strip():
            coment[atual_c].append(l)

    problemas = []
    for n, q in sorted(questoes.items()):
        if n not in gab:
            problemas.append(f"questão {n} sem gabarito comentado")
            continue
        if n in rapido and rapido[n] != gab[n]:
            problemas.append(f"questão {n}: gabarito rápido e comentado não batem")
        q["gabarito"] = gab[n]
        q["comentario"] = juntar(coment[n])
        q["assunto"] = pontos.get(q["ponto"], "")
        if not q.get("enunciado"):
            problemas.append(f"questão {n} sem enunciado")
    sobrando = set(gab) - set(questoes)
    if sobrando:
        problemas.append(f"gabarito de questões que não achei: {sorted(sobrando)[:10]}")
    numeros = sorted(questoes)
    if numeros and numeros != list(range(1, len(numeros) + 1)):
        faltam = sorted(set(range(1, numeros[-1] + 1)) - set(numeros))
        problemas.append(f"numeração com falhas: faltam {faltam[:10]}")
    if problemas:
        raise ErroFormato("; ".join(problemas))

    return materia, [questoes[n] for n in numeros]


def main():
    pdfs = sorted(PASTA_PDFS.glob("*.pdf"))
    if not pdfs:
        sys.exit(f"Nenhum PDF em {PASTA_PDFS}")

    materias = {}
    todas = []
    erros = []
    for pdf in pdfs:
        try:
            materia, qs = analisar(pdf)
        except ErroFormato as e:
            erros.append(f"{pdf.name}: {e}")
            continue
        mid = slug(materia)
        materias.setdefault(mid, materia)
        vistos = {}
        for q in qs:
            # Identificador estável: matéria + texto do enunciado. Assim o progresso
            # é mantido mesmo que a questão mude de número ou de arquivo.
            base = mid + "|" + normalizar(q["enunciado"])
            vistos[base] = vistos.get(base, 0) + 1
            if vistos[base] > 1:
                base += f"|{vistos[base]}"
            qid = mid + "-" + hashlib.sha1(base.encode()).hexdigest()[:12]
            todas.append({
                "id": qid,
                "materia": mid,
                "arquivo": pdf.name,
                **q,
            })
        print(f"{pdf.name}: {materia} · {len(qs)} questões")

    if erros:
        print("\nNão foi possível ler:", file=sys.stderr)
        for e in erros:
            print("  " + e, file=sys.stderr)
        sys.exit(1)

    ids = [q["id"] for q in todas]
    if len(ids) != len(set(ids)):
        sys.exit("Identificadores repetidos: há o mesmo PDF duas vezes?")

    def chave_materia(item):
        mid, nome = item
        return (ORDEM_MATERIAS.index(nome) if nome in ORDEM_MATERIAS else len(ORDEM_MATERIAS), nome)

    lista_materias = [
        {"id": mid, "nome": nome, "ordem": i + 1}
        for i, (mid, nome) in enumerate(sorted(materias.items(), key=chave_materia))
    ]
    ordem = {m["id"]: m["ordem"] for m in lista_materias}
    todas.sort(key=lambda q: (ordem[q["materia"]], q["arquivo"], q["numero"]))
    for q in todas:
        del q["arquivo"]

    conteudo = {"materias": lista_materias, "questoes": todas}
    texto = json.dumps(conteudo, ensure_ascii=False, separators=(",", ":"))
    versao = hashlib.sha1(texto.encode()).hexdigest()[:12]
    conteudo = {"versao": versao, **conteudo}
    SAIDA.parent.mkdir(parents=True, exist_ok=True)
    SAIDA.write_text(json.dumps(conteudo, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"\n{len(todas)} questões em {len(lista_materias)} matérias -> {SAIDA.relative_to(RAIZ)}")


if __name__ == "__main__":
    main()
