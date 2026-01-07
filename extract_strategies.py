import PyPDF2
import os

pdf_files = [
    "Gap_Give_and_Go_Cheat_Sheet.pdf",
    "MACD-Cheat-Sheet.pdf",
    "Technical-Analysis-Range-Break-Trading-Cheat-Sheet.pdf",
    "The-Fashionably-Late-Scalp-Cheat-Sheet.pdf",
    "The_Big_Dog_Consolidation_Cheat_Sheet.pdf",
    "back$ide_cheat_sheet.pdf",
    "hitchhiker_scalp_cheat_sheet.pdf",
    "the_rubberband_scalp_cheat_sheet.pdf",
    "the_second_chance_scalp_cheat_sheet.pdf"
]

def extract_text(pdf_path):
    try:
        with open(pdf_path, 'rb') as file:
            reader = PyPDF2.PdfReader(file)
            text = ""
            for page in reader.pages:
                text += page.extract_text() + "\n"
            return text
    except Exception as e:
        return f"Error reading {pdf_path}: {str(e)}"

with open("STRATEGIES_EXTRACTED.md", "w", encoding="utf-8") as out:
    for pdf in pdf_files:
        full_path = os.path.join(r"d:\Java_CompleteProject\Java_CompleteSystem", pdf)
        if os.path.exists(full_path):
            out.write(f"# Strategy: {pdf}\n\n")
            out.write(extract_text(full_path))
            out.write("\n\n---\n\n")
        else:
            out.write(f"# Strategy: {pdf} (NOT FOUND)\n\n---\n\n")

print("Extraction completed. Check STRATEGIES_EXTRACTED.md")
