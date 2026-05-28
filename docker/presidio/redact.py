#!/usr/bin/env python3
"""Redact PII from a PDF using Presidio + PyMuPDF."""
import argparse
import fitz  # PyMuPDF
from presidio_analyzer import AnalyzerEngine
from presidio_anonymizer import AnonymizerEngine

def redact_pdf(input_path, output_path, entities, threshold, label):
    analyzer = AnalyzerEngine()
    doc = fitz.open(input_path)
    for page in doc:
        text = page.get_text()
        results = analyzer.analyze(text=text, language="en",
                                   entities=entities, score_threshold=threshold)
        for result in results:
            hits = page.search_for(text[result.start:result.end])
            for hit in hits:
                page.add_redact_annot(hit, fill=(0, 0, 0))
        page.apply_redactions()
    doc.save(output_path)
    doc.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--entities", default="PERSON,PHONE_NUMBER,EMAIL_ADDRESS")
    parser.add_argument("--threshold", type=float, default=0.5)
    parser.add_argument("--label", default="[REDACTED]")
    args = parser.parse_args()
    entity_list = [e.strip() for e in args.entities.split(",")]
    redact_pdf(args.input, args.output, entity_list, args.threshold, args.label)
    print(f"Redacted PDF written to {args.output}")
