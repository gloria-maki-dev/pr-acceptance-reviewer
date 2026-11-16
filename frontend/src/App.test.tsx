import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import type { ReviewResponse } from './types';

const RESPUESTA_CON_FALLO: ReviewResponse = {
  model: 'claude-opus-5-5',
  results: [
    {
      criterion: 'Cambiar el texto «Total» a «Total estimado» en el resumen del carrito.',
      status: 'MET',
      explanation: 'El resumen del carrito pasa a decir «Total estimado».',
      evidence: {
        file: 'src/components/CartSummary.tsx',
        side: 'ADDED',
        lineNumber: 20,
        snippet: '<span className="cart-summary__label">Total estimado</span>',
      },
      verificationNote: null,
    },
    {
      criterion: 'Limitar el cambio al resumen del carrito.',
      status: 'NOT_MET',
      explanation: 'El historial de pedidos también cambia.',
      evidence: {
        file: 'src/components/OrderHistory.tsx',
        side: 'ADDED',
        lineNumber: 32,
        snippet: 'Total estimado: {formatPrice(order.total)}',
      },
      verificationNote: null,
    },
  ],
};

const DIFF_LOGIN = `diff --git a/src/login/messages.ts b/src/login/messages.ts
--- a/src/login/messages.ts
+++ b/src/login/messages.ts
@@ -1,4 +1,4 @@
 export const loginMessages = {
-  wrongPassword: 'Error de inicio de sesion',
-  accountLocked: 'Tu cuenta esta bloqueada',
+  wrongPassword: 'La contrasena es incorrecta',
+  accountLocked: 'Intentalo mas tarde',
 };`;

const RESPUESTA_LOGIN: ReviewResponse = {
  model: 'claude-opus-5-5',
  results: [
    {
      criterion: 'El mensaje de contraseña incorrecta debe decir «La contraseña es incorrecta».',
      status: 'MET',
      explanation: 'El mensaje pasa a decir «La contraseña es incorrecta».',
      evidence: {
        file: 'src/login/messages.ts',
        side: 'ADDED',
        lineNumber: 2,
        snippet: "wrongPassword: 'La contraseña es incorrecta'",
      },
      verificationNote: null,
    },
    {
      criterion: 'El mensaje de cuenta bloqueada debe seguir diciendo «Tu cuenta está bloqueada».',
      status: 'NOT_MET',
      explanation: 'El mensaje de cuenta bloqueada pasa a decir «Inténtalo más tarde».',
      evidence: {
        file: 'src/login/messages.ts',
        side: 'ADDED',
        lineNumber: 3,
        snippet: "accountLocked: 'Inténtalo más tarde'",
      },
      verificationNote: null,
    },
  ],
};

function mockFetchOnce(status: number, body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('empieza invitando a cargar el ejemplo, con «Revisar PR» apagado', () => {
    render(<App />);

    expect(screen.getByText(/Pega un diff y pulsa/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revisar PR' })).toBeDisabled();
  });

  it('«Probar ejemplo» carga el diff que toca los dos archivos', async () => {
    render(<App />);

    await userEvent.click(screen.getByRole('button', { name: 'Probar ejemplo' }));

    const diff = screen.getByLabelText('Diff') as HTMLTextAreaElement;
    expect(diff.value).toContain('src/components/CartSummary.tsx');
    expect(diff.value).toContain('src/components/OrderHistory.tsx');
    expect(screen.getByRole('button', { name: 'Revisar PR' })).toBeEnabled();
  });

  it('«Cargar diff corregido» deja solo el resumen del carrito', async () => {
    render(<App />);

    await userEvent.click(screen.getByRole('button', { name: 'Cargar diff corregido' }));

    const diff = screen.getByLabelText('Diff') as HTMLTextAreaElement;
    expect(diff.value).toContain('src/components/CartSummary.tsx');
    expect(diff.value).not.toContain('OrderHistory.tsx');
  });

  it('enseña un veredicto por criterio con su cita', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Probar ejemplo' }));

    const fetchMock = mockFetchOnce(200, RESPUESTA_CON_FALLO);
    await userEvent.click(screen.getByRole('button', { name: 'Revisar PR' }));

    await waitFor(() => expect(screen.getByText('Cumplido')).toBeInTheDocument());
    expect(screen.getByText('No cumplido')).toBeInTheDocument();
    expect(screen.getByText('src/components/OrderHistory.tsx')).toBeInTheDocument();
    expect(screen.getByText('32')).toBeInTheDocument();
    expect(screen.getByText(/Revisado por claude-opus-5-5/)).toBeInTheDocument();

    const [, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(JSON.parse(options.body as string).acceptanceCriteria).toHaveLength(2);
  });

  it('oculta la cita rechazada y la explicación que se apoyaba en ella', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Probar ejemplo' }));

    mockFetchOnce(200, {
      model: 'claude-opus-5-5',
      results: [
        {
          criterion: 'Un criterio',
          status: 'UNVERIFIED',
          explanation: 'Cambiado.',
          evidence: {
            file: 'src/components/CartSummary.tsx',
            side: 'ADDED',
            lineNumber: 417,
            snippet: 'Total estimado',
          },
          verificationNote: 'En «src/components/CartSummary.tsx» no hay ninguna línea 417.',
        },
      ],
    } satisfies ReviewResponse);

    await userEvent.click(screen.getByRole('button', { name: 'Revisar PR' }));

    await waitFor(() => expect(screen.getByText('No verificado')).toBeInTheDocument());
    expect(screen.getByText(/Sin evidencia que lo sostenga/)).toBeInTheDocument();
    expect(screen.getByText(/no hay ninguna línea 417/)).toBeInTheDocument();
    // Ni la cita rechazada ni la explicación que dependía de ella llegan a la pantalla.
    expect(screen.queryByText('src/components/CartSummary.tsx')).not.toBeInTheDocument();
    expect(screen.queryByText('417')).not.toBeInTheDocument();
    expect(screen.queryByText('Cambiado.')).not.toBeInTheDocument();
  });

  it('revisar un diff de login tras el ejemplo solo enseña evidencia de messages.ts', async () => {
    render(<App />);

    // Primero la revisión del ejemplo TIENDA-412.
    await userEvent.click(screen.getByRole('button', { name: 'Probar ejemplo' }));
    mockFetchOnce(200, RESPUESTA_CON_FALLO);
    await userEvent.click(screen.getByRole('button', { name: 'Revisar PR' }));
    await waitFor(() => expect(screen.getByText('src/components/OrderHistory.tsx')).toBeInTheDocument());

    // Ahora unos criterios y un diff propios.
    fireEvent.change(screen.getByLabelText('Criterios de aceptación'), {
      target: {
        value:
          'El mensaje de contraseña incorrecta debe decir «La contraseña es incorrecta».\n' +
          'El mensaje de cuenta bloqueada debe seguir diciendo «Tu cuenta está bloqueada».',
      },
    });
    fireEvent.change(screen.getByLabelText('Diff'), { target: { value: DIFF_LOGIN } });

    const fetchMock = mockFetchOnce(200, RESPUESTA_LOGIN);
    await userEvent.click(screen.getByRole('button', { name: 'Revisar PR' }));

    await waitFor(() => expect(screen.getAllByText('src/login/messages.ts')).toHaveLength(2));
    expect(screen.queryByText('src/components/CartSummary.tsx')).not.toBeInTheDocument();
    expect(screen.queryByText('src/components/OrderHistory.tsx')).not.toBeInTheDocument();
    expect(screen.getByText('Cumplido')).toBeInTheDocument();
    expect(screen.getByText('No cumplido')).toBeInTheDocument();

    // La petición lleva los criterios y el diff nuevos, no los del ejemplo.
    const [, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    const enviado = JSON.parse(options.body as string);
    expect(enviado.diff).toContain('src/login/messages.ts');
    expect(enviado.diff).not.toContain('CartSummary.tsx');
    expect(enviado.acceptanceCriteria[0]).toContain('La contraseña es incorrecta');
  });

  it('tras un error del proveedor se puede reintentar sin recargar', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Probar ejemplo' }));

    mockFetchOnce(503, { error: 'No se pudo hablar con el modelo.' });
    await userEvent.click(screen.getByRole('button', { name: 'Revisar PR' }));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());

    mockFetchOnce(200, RESPUESTA_CON_FALLO);
    await userEvent.click(screen.getByRole('button', { name: 'Revisar PR' }));

    await waitFor(() => expect(screen.getByText('Cumplido')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('enseña el mensaje que manda el servidor cuando algo falla', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Probar ejemplo' }));

    mockFetchOnce(503, { error: 'Falta la variable de entorno ANTHROPIC_API_KEY.' });
    await userEvent.click(screen.getByRole('button', { name: 'Revisar PR' }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Falta la variable de entorno ANTHROPIC_API_KEY.',
      ),
    );
  });

  it('explica qué hacer si el backend no está arrancado', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Probar ejemplo' }));

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await userEvent.click(screen.getByRole('button', { name: 'Revisar PR' }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/mvn spring-boot:run/),
    );
  });
});
