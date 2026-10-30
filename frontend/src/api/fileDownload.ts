import { ApiError } from './errors';

/** attachment의 이름을 다운로드 문맥에서만 정리한다. 임의 URL이나 서버 경로로 사용하지 않는다. */
export function readDownloadName(disposition: string, contentType: string): string {
  if (!/^attachment(?:;|$)/i.test(disposition)) throw new ApiError('UNEXPECTED_ERROR');

  const encoded = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(disposition)?.[1];
  let name = /filename\s*=\s*"([^"]*)"/i.exec(disposition)?.[1] ?? '';

  if (encoded) {
    try {
      name = decodeURIComponent(encoded.trim());
    } catch {
      throw new ApiError('UNEXPECTED_ERROR');
    }
  }

  const extension = contentType === 'image/png' ? '.png' : '.jpg';
  const cleaned = name
    .replace(/[<>:"/\\|?*\u0000-\u001f\u007f]/g, '_')
    .replace(/^[. ]+|[. ]+$/g, '');
  const clean = Array.from(cleaned).slice(0, 120).join('');
  const safe =
    !clean || /^(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(clean) ? 'image' : clean;

  return /\.(?:png|jpe?g)$/i.test(safe) ? safe : `${safe}${extension}`;
}

/** 검증한 응답 바이트만 blob URL로 저장하며 URL을 지연 해제해 다운로드 시작을 보장한다. */
export function saveFileDownload(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;

  try {
    document.body.appendChild(anchor);
    anchor.click();
  } finally {
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
