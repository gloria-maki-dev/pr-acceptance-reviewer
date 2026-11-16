import { describe, expect, it } from 'vitest';
import { parseCriteria } from './api';

describe('parseCriteria', () => {
  it('toma un criterio por línea', () => {
    expect(parseCriteria('Uno\nDos')).toEqual(['Uno', 'Dos']);
  });

  it('se salta las líneas vacías y los espacios de los extremos', () => {
    expect(parseCriteria('  Uno  \n\n   \nDos\n')).toEqual(['Uno', 'Dos']);
  });

  it('devuelve una lista vacía cuando no hay nada', () => {
    expect(parseCriteria('   \n  ')).toEqual([]);
  });
});
