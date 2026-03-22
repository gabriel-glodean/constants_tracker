export interface UploadResult {
  status: 'success' | 'error';
  message: string;
}

export async function uploadClass({
  file,
  project,
  version,
}: {
  file: File;
  project: string;
  version?: number;
}): Promise<UploadResult> {
  const url = version != null
    ? `/class?project=${encodeURIComponent(project)}&version=${version}`
    : `/class?project=${encodeURIComponent(project)}`;
  const res = await fetch(url, {
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
}: {
  file: File;
  project: string;
}): Promise<UploadResult> {
  const url = `/jar?project=${encodeURIComponent(project)}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/octet-stream' },
    body: file,
  });
  if (res.ok) return { status: 'success', message: 'JAR uploaded successfully.' };
  if (res.status === 422) return { status: 'error', message: 'Invalid JAR file.' };
  return { status: 'error', message: `Upload failed (HTTP ${res.status})` };
}

