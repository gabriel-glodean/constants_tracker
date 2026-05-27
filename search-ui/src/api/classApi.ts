export interface ClassConstantsLookup {
  className: string;
  project: string;
  version: number;
}

export interface SemanticTypeInfo {
  kind: string;
  name: string;
  displayName: string | null;
  description: string | null;
}

export interface UsageInfo {
  structuralType: string;
  semanticType: SemanticTypeInfo | null;
}

export interface ConstantEntry {
  value: string;
  valueType: string;
  usages: UsageInfo[];
}

export interface ClassConstantsReply {
  constants: ConstantEntry[];
}

export async function getClassConstants(
  params: ClassConstantsLookup,
  options?: { fetcher?: typeof fetch }
): Promise<ClassConstantsReply> {
  const fetcher = options?.fetcher ?? globalThis.fetch;
  const stringParams: Record<string, string> = Object.fromEntries(
    Object.entries(params).map(([k, v]) => [k, String(v)])
  );
  const query = new URLSearchParams(stringParams).toString();
  const res = await fetcher(`/class?${query}`, {
    method: 'GET',
  });
  if (!res.ok) {
    if (res.status === 404) throw new Error('Class/version not found.');
    throw new Error(`Lookup failed (HTTP ${res.status})`);
  }
  return res.json();
}
