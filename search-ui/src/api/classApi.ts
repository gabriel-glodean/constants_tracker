export interface ClassConstantsLookup {
  className: string;
  project: string;
  version: number;
}

export interface ClassConstantsReply {
  constants: Record<string, string[]>;
}

export async function getClassConstants(
  params: ClassConstantsLookup
): Promise<ClassConstantsReply> {
  const res = await fetch('/class', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  });
  if (!res.ok) {
    if (res.status === 404) throw new Error('Class/version not found.');
    throw new Error(`Lookup failed (HTTP ${res.status})`);
  }
  return res.json();
}

