import { Injectable } from '@angular/core';

// NOTE: jsPDF is loaded from a CDN in this app (not an npm dep), so `doc` is typed as `any`.

declare global {
  interface Window {
    ReactNativeWebView?: { postMessage: (msg: string) => void };
  }
}

/**
 * Saves PDFs so they download both in a normal browser AND inside the native
 * Layla Android WebView. The native app exposes window.ReactNativeWebView.postMessage
 * and accepts: { type:'PDF_BASE64', payload:'<raw base64>', fileName:'x.pdf' }.
 */
@Injectable({ providedIn: 'root' })
export class PdfService {

  /** True when running inside the native WebView (the JS bridge is present). */
  get inWebView(): boolean {
    return !!window.ReactNativeWebView?.postMessage;
  }

  /** Save a jsPDF document (CDN jsPDF instance). */
  savePdf(doc: any, fileName = 'document.pdf'): void {
    const bridge = window.ReactNativeWebView;
    if (bridge?.postMessage) {
      const base64 = doc.output('dataurlstring').split(',')[1];
      bridge.postMessage(JSON.stringify({ type: 'PDF_BASE64', payload: base64, fileName }));
    } else {
      doc.save(fileName); // normal browser download
    }
  }

  /** Save a PDF Blob (e.g. one fetched from the backend receipt endpoint). */
  saveBlob(blob: Blob, fileName = 'document.pdf'): void {
    const bridge = window.ReactNativeWebView;
    if (bridge?.postMessage) {
      const reader = new FileReader();
      reader.onloadend = () => {
        const base64 = ((reader.result as string) || '').split(',')[1] || '';
        bridge.postMessage(JSON.stringify({ type: 'PDF_BASE64', payload: base64, fileName }));
      };
      reader.readAsDataURL(blob);
    } else {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
  }
}
