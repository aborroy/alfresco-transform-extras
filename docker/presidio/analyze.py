#!/usr/bin/env python3
"""Analyze PII in a PDF using Presidio + PyMuPDF."""
import argparse
import json
import fitz  # PyMuPDF
from presidio_analyzer import AnalyzerEngine

def analyze_pdf(input_path):
    analyzer = AnalyzerEngine()
    doc = fitz.open(input_path)
    all_results = []
    for page in doc:
        text = page.get_text()
        results = analyzer.analyze(text=text, language="en")
        all_results.extend(results)
    doc.close()

    entity_types = list({r.entity_type for r in all_results})
    scores = [r.score for r in all_results]
    return {
        "hasPII": len(all_results) > 0,
        "entities": ",".join(entity_types),
        "scoreMax": max(scores) if scores else 0.0,
        "scoreAvg": sum(scores) / len(scores) if scores else 0.0,
        "count": len(all_results)
    }

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    args = parser.parse_args()
    result = analyze_pdf(args.input)
    print(json.dumps(result))
