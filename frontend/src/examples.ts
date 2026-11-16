import criteriosRaw from '../../examples/criterios.txt?raw';
import diffConFallo from '../../examples/diff-con-fallo.diff?raw';
import diffCorregido from '../../examples/diff-corregido.diff?raw';

/**
 * El ticket de la demo. Los archivos viven en examples/, en la raíz del repositorio, y son los
 * mismos que usan las pruebas del backend.
 */
export const TICKET = {
  id: 'TIENDA-412',
  title: 'Aclarar el importe del carrito',
  criteria: criteriosRaw.trim(),
};

export const DIFF_CON_FALLO = diffConFallo.trimEnd();
export const DIFF_CORREGIDO = diffCorregido.trimEnd();
