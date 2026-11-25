import { Line } from './report';

/** One difference, told in a sentence a company manager can read. */
export interface Finding {
  key: string;
  /** Word shown on the coloured rule: what kind of difference this is. */
  kind: 'gap' | 'missing' | 'extra';
  status: string;
  title: string;
  sentence: string;
  hint: string;
}

const EURO = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });

export function euros(value: number): string {
  return EURO.format(Math.abs(value));
}

/** Turns the lines that differ into sentences; matching lines are left out. */
export function findings(lines: Line[], scope: 'account' | 'thirdParty'): Finding[] {
  return lines.filter((l) => l.status !== 'OK').map((l) => describe(l, scope));
}

function describe(line: Line, scope: 'account' | 'thirdParty'): Finding {
  const title = scope === 'account' ? accountTitle(line) : thirdPartyTitle(line);
  const hint =
    scope === 'account' && line.key.startsWith('471')
      ? "Un compte d'attente sert souvent à ranger ce que la reprise n'a pas su placer."
      : '';
  switch (line.status) {
    case 'MISSING_IN_NEW':
      return {
        key: `${scope}-${line.key}`,
        kind: 'missing',
        status: 'Manque dans le nouveau',
        title,
        sentence: `${euros(line.oldBalance)} dans l'ancien logiciel, introuvable dans le nouveau.`,
        hint,
      };
    case 'MISSING_IN_OLD':
      return {
        key: `${scope}-${line.key}`,
        kind: 'extra',
        status: 'Apparu dans le nouveau',
        title,
        sentence: `${euros(line.newBalance)} dans le nouveau logiciel, absent de l'ancien.`,
        hint,
      };
    default:
      return {
        key: `${scope}-${line.key}`,
        kind: 'gap',
        status: 'Montant différent',
        title,
        sentence: `${euros(line.oldBalance)} avant la bascule, ${euros(line.newBalance)} après : écart de ${euros(line.difference)}.`,
        hint,
      };
  }
}

function accountTitle(line: Line): string {
  const name = line.label || 'Compte sans libellé';
  const renamed = line.oldAccounts.filter((a) => a !== line.key);
  const old = renamed.length > 0 ? `, ancien ${renamed.join(' + ')}` : '';
  return `${name} (compte ${line.key}${old})`;
}

function thirdPartyTitle(line: Line): string {
  return line.label ? `${line.label} (tiers ${line.key})` : `Tiers ${line.key}`;
}
