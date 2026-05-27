import { cn } from './utils'

describe('cn', () => {
  it('merges class names', () => {
    expect(cn('foo', 'bar')).toBe('foo bar')
  })

  it('handles conditional classes (clsx falsy values)', () => {
    expect(cn('foo', false && 'bar', 'baz')).toBe('foo baz')
    expect(cn('foo', undefined, null, 'baz')).toBe('foo baz')
  })

  it('deduplicates conflicting Tailwind classes via twMerge', () => {
    // twMerge should resolve conflicting utilities, keeping the last
    const result = cn('p-2', 'p-4')
    expect(result).toBe('p-4')
  })

  it('returns empty string with no arguments', () => {
    expect(cn()).toBe('')
  })
})

