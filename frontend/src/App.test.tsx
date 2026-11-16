import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import type { ReviewResponse } from './types';

const ANSWER_WITH_BUG: ReviewResponse = {
  model: 'claude-opus-5-5',
  provider: 'Anthropic API',
  officialProvider: true,
  results: [
    {
      criterion: 'Change the text "Total" to "Estimated total" in the cart summary.',
      status: 'MET',
      explanation: 'The cart summary now reads "Estimated total".',
      evidence: {
        file: 'src/components/CartSummary.tsx',
        side: 'ADDED',
        lineNumber: 20,
        snippet: '<span className="cart-summary__label">Estimated total</span>',
      },
      verificationNote: null,
    },
    {
      criterion: 'Keep the change to the cart summary only.',
      status: 'NOT_MET',
      explanation: 'Order history changes as well.',
      evidence: {
        file: 'src/components/OrderHistory.tsx',
        side: 'ADDED',
        lineNumber: 32,
        snippet: 'Estimated total: {formatPrice(order.total)}',
      },
      verificationNote: null,
    },
  ],
};

const LOGIN_DIFF = `diff --git a/src/login/messages.ts b/src/login/messages.ts
--- a/src/login/messages.ts
+++ b/src/login/messages.ts
@@ -1,4 +1,4 @@
 export const loginMessages = {
-  wrongPassword: 'Sign-in error',
-  accountLocked: 'Your account is locked',
+  wrongPassword: 'The password is incorrect',
+  accountLocked: 'Try again later',
 };`;

const LOGIN_ANSWER: ReviewResponse = {
  model: 'claude-opus-5-5',
  provider: 'Anthropic API',
  officialProvider: true,
  results: [
    {
      criterion: 'The wrong-password message must read "The password is incorrect".',
      status: 'MET',
      explanation: 'The message now reads "The password is incorrect".',
      evidence: {
        file: 'src/login/messages.ts',
        side: 'ADDED',
        lineNumber: 2,
        snippet: "wrongPassword: 'The password is incorrect'",
      },
      verificationNote: null,
    },
    {
      criterion: 'The locked-account message must still read "Your account is locked".',
      status: 'NOT_MET',
      explanation: 'The locked-account message now reads "Try again later".',
      evidence: {
        file: 'src/login/messages.ts',
        side: 'ADDED',
        lineNumber: 3,
        snippet: "accountLocked: 'Try again later'",
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

  it('starts by inviting you to load the example, with "Review PR" disabled', () => {
    render(<App />);

    expect(screen.getByText(/Paste a diff and press/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Review PR' })).toBeDisabled();
  });

  it('"Try example" loads the diff that touches both files', async () => {
    render(<App />);

    await userEvent.click(screen.getByRole('button', { name: 'Try example' }));

    const diff = screen.getByLabelText('Diff') as HTMLTextAreaElement;
    expect(diff.value).toContain('src/components/CartSummary.tsx');
    expect(diff.value).toContain('src/components/OrderHistory.tsx');
    expect(screen.getByRole('button', { name: 'Review PR' })).toBeEnabled();
  });

  it('"Load fixed diff" leaves only the cart summary', async () => {
    render(<App />);

    await userEvent.click(screen.getByRole('button', { name: 'Load fixed diff' }));

    const diff = screen.getByLabelText('Diff') as HTMLTextAreaElement;
    expect(diff.value).toContain('src/components/CartSummary.tsx');
    expect(diff.value).not.toContain('OrderHistory.tsx');
  });

  it('shows one verdict per criterion with its citation', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Try example' }));

    const fetchMock = mockFetchOnce(200, ANSWER_WITH_BUG);
    await userEvent.click(screen.getByRole('button', { name: 'Review PR' }));

    await waitFor(() => expect(screen.getByText('Met')).toBeInTheDocument());
    expect(screen.getByText('Not met')).toBeInTheDocument();
    expect(screen.getByText('src/components/OrderHistory.tsx')).toBeInTheDocument();
    expect(screen.getByText('32')).toBeInTheDocument();
    expect(screen.getByText(/Reviewed by claude-opus-5-5/)).toBeInTheDocument();

    const [, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(JSON.parse(options.body as string).acceptanceCriteria).toHaveLength(2);
  });

  it('hides a rejected citation and the explanation that rested on it', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Try example' }));

    mockFetchOnce(200, {
      model: 'claude-opus-5-5',
      provider: 'Anthropic API',
      officialProvider: true,
      results: [
        {
          criterion: 'A criterion',
          status: 'NOT_VERIFIED',
          explanation: 'Changed.',
          evidence: {
            file: 'src/components/CartSummary.tsx',
            side: 'ADDED',
            lineNumber: 417,
            snippet: 'Estimated total',
          },
          verificationNote: '`src/components/CartSummary.tsx` has no ADDED line 417 in this diff.',
        },
      ],
    } satisfies ReviewResponse);

    await userEvent.click(screen.getByRole('button', { name: 'Review PR' }));

    await waitFor(() => expect(screen.getByText('Not verified')).toBeInTheDocument());
    expect(screen.getByText(/No evidence to back it/)).toBeInTheDocument();
    expect(screen.getByText(/no ADDED line 417/)).toBeInTheDocument();
    // Neither the rejected citation nor the explanation that depended on it reach the screen.
    expect(screen.queryByText('src/components/CartSummary.tsx')).not.toBeInTheDocument();
    expect(screen.queryByText('417')).not.toBeInTheDocument();
    expect(screen.queryByText('Changed.')).not.toBeInTheDocument();
  });

  it('reviewing a login diff after the example only shows evidence from messages.ts', async () => {
    render(<App />);

    await userEvent.click(screen.getByRole('button', { name: 'Try example' }));
    mockFetchOnce(200, ANSWER_WITH_BUG);
    await userEvent.click(screen.getByRole('button', { name: 'Review PR' }));
    await waitFor(() => expect(screen.getByText('src/components/OrderHistory.tsx')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText('Acceptance criteria'), {
      target: {
        value:
          'The wrong-password message must read "The password is incorrect".\n' +
          'The locked-account message must still read "Your account is locked".',
      },
    });
    fireEvent.change(screen.getByLabelText('Diff'), { target: { value: LOGIN_DIFF } });

    const fetchMock = mockFetchOnce(200, LOGIN_ANSWER);
    await userEvent.click(screen.getByRole('button', { name: 'Review PR' }));

    await waitFor(() => expect(screen.getAllByText('src/login/messages.ts')).toHaveLength(2));
    expect(screen.queryByText('src/components/CartSummary.tsx')).not.toBeInTheDocument();
    expect(screen.queryByText('src/components/OrderHistory.tsx')).not.toBeInTheDocument();
    expect(screen.getByText('Met')).toBeInTheDocument();
    expect(screen.getByText('Not met')).toBeInTheDocument();

    const [, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    const sent = JSON.parse(options.body as string);
    expect(sent.diff).toContain('src/login/messages.ts');
    expect(sent.diff).not.toContain('CartSummary.tsx');
    expect(sent.acceptanceCriteria[0]).toContain('The password is incorrect');
  });

  it('shows all three criteria even when the model only evaluates two', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Try example' }));

    mockFetchOnce(200, {
      model: 'claude-opus-5-5',
      provider: 'Anthropic API',
      officialProvider: true,
      results: [
        {
          criterion: 'The exportButtonLabel constant must have the value "Download CSV".',
          status: 'MET',
          explanation: 'The label was updated.',
          evidence: {
            file: 'src/orders/exportCsv.ts',
            side: 'ADDED',
            lineNumber: 1,
            snippet: "exportButtonLabel = 'Download CSV'",
          },
          verificationNote: null,
        },
        {
          criterion: 'The CSV separator must still be the semicolon.',
          status: 'NOT_MET',
          explanation: 'The separator became a comma.',
          evidence: {
            file: 'src/orders/exportCsv.ts',
            side: 'ADDED',
            lineNumber: 2,
            snippet: "const separator = ','",
          },
          verificationNote: null,
        },
        {
          criterion: 'The export endpoint must require authentication.',
          status: 'NOT_VERIFIED',
          explanation: null,
          evidence: null,
          verificationNote: 'The model did not evaluate this criterion: it returned no verdict for it.',
        },
      ],
    } satisfies ReviewResponse);

    await userEvent.click(screen.getByRole('button', { name: 'Review PR' }));

    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(3));
    expect(screen.getByText('Met')).toBeInTheDocument();
    expect(screen.getByText('Not met')).toBeInTheDocument();
    expect(screen.getByText('Not verified')).toBeInTheDocument();
    expect(screen.getByText(/did not evaluate this criterion/)).toBeInTheDocument();
    expect(screen.getByText('Not checked.')).toBeInTheDocument();
  });

  it('warns on screen when the Anthropic API did not answer', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Try example' }));

    mockFetchOnce(200, {
      ...ANSWER_WITH_BUG,
      provider: 'Simulated provider at localhost:9191',
      officialProvider: false,
    } satisfies ReviewResponse);

    await userEvent.click(screen.getByRole('button', { name: 'Review PR' }));

    await waitFor(() =>
      expect(screen.getByText(/This was not reviewed by the Anthropic API/)).toBeInTheDocument(),
    );
    expect(screen.getByRole('note')).toHaveTextContent('Simulated provider at localhost:9191');
    expect(screen.queryByText(/Reviewed by claude-opus-5-5, via/)).not.toBeInTheDocument();
  });

  it('shows the message the server sends when something fails', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Try example' }));

    mockFetchOnce(503, { error: 'The ANTHROPIC_API_KEY environment variable is missing.' });
    await userEvent.click(screen.getByRole('button', { name: 'Review PR' }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(
        'The ANTHROPIC_API_KEY environment variable is missing.',
      ),
    );
  });

  it('explains what to do when the backend is not running', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Try example' }));

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await userEvent.click(screen.getByRole('button', { name: 'Review PR' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/mvn spring-boot:run/));
  });

  it('lets you retry after a provider error without reloading', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Try example' }));

    mockFetchOnce(503, { error: 'Could not reach the model.' });
    await userEvent.click(screen.getByRole('button', { name: 'Review PR' }));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());

    mockFetchOnce(200, ANSWER_WITH_BUG);
    await userEvent.click(screen.getByRole('button', { name: 'Review PR' }));

    await waitFor(() => expect(screen.getByText('Met')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
