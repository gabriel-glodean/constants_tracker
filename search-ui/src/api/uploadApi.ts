export interface UploadResult {
  status: 'success' | 'error';
  message: string;
}

export async function uploadClass({
  file,
  project,
  version,
  fetcher = globalThis.fetch,
}: {
  file: File;
  project: string;
  version?: number;
  fetcher?: typeof fetch;
}): Promise<UploadResult> {
  const url = version != null
    ? `/class?project=${encodeURIComponent(project)}&version=${version}`
    : `/class?project=${encodeURIComponent(project)}`;
  const res = await fetcher(url, {
    method: version != null ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/octet-stream' },
    body: file,
  });
  if (res.ok) return { status: 'success', message: 'Class uploaded successfully.' };
  if (res.status === 422) return { status: 'error', message: 'Invalid class file.' };
  return { status: 'error', message: `Upload failed (HTTP ${res.status})` };
}

export async function uploadJar({
  file,
  project,
  jarName,
  fetcher = globalThis.fetch,
}: {
  file: File;
  project: string;
  jarName: string;
  fetcher?: typeof fetch;
}): Promise<UploadResult> {
  const url = `/jar?project=${encodeURIComponent(project)}&jarName=${encodeURIComponent(jarName)}`;
  const res = await fetcher(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/octet-stream' },
    body: file,
  });
  if (res.ok) return { status: 'success', message: 'JAR uploaded successfully.' };
  if (res.status === 422) return { status: 'error', message: 'Invalid JAR file.' };
  return { status: 'error', message: `Upload failed (HTTP ${res.status})` };
}

export async function uploadConfig({
  file,
  project,
  version,
  fetcher = globalThis.fetch,
}: {
  file: File;
  project: string;
  version?: number;
  fetcher?: typeof fetch;
}): Promise<UploadResult> {
  const form = new FormData();
  form.append('file', file);
  const url = version != null
    ? `/config?project=${encodeURIComponent(project)}&version=${version}`
    : `/config?project=${encodeURIComponent(project)}`;
  const res = await fetcher(url, {
    method: version != null ? 'PUT' : 'POST',
    body: form,
  });
  if (res.ok) return { status: 'success', message: 'Config file uploaded successfully.' };
  if (res.status === 422) return { status: 'error', message: 'Unsupported config file type.' };
  return { status: 'error', message: `Upload failed (HTTP ${res.status})` };
}
