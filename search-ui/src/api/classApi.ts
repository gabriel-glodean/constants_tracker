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
  const stringParams: Record<string, string> = Object.fromEntries(
    Object.entries(params).map(([k, v]) => [k, String(v)])
  );
  const query = new URLSearchParams(stringParams).toString();
  const res = await fetch(`/class?${query}`, {
    method: 'GET',
  });
  if (!res.ok) {
    if (res.status === 404) throw new Error('Class/version not found.');
    throw new Error(`Lookup failed (HTTP ${res.status})`);
  }
  return res.json();
}

