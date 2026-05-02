import { render, screen, act } from '@testing-library/react';
import App from './App';

test('renders Constant Tracker heading', async () => {
  await act(async () => { render(<App />); });
  // Use getAllByText to avoid error when multiple matches exist
  const headings = screen.getAllByText(/constant tracker/i);
  expect(headings.length).toBeGreaterThan(0);
});
