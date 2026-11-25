import { Component, input, model, output, signal } from '@angular/core';

/** A large zone that takes one file, by drag and drop or by click. */
@Component({
  selector: 'app-file-drop',
  host: { class: 'drop-host' },
  template: `
    <label
      class="drop"
      [class.drop-over]="over()"
      [class.drop-filled]="file() !== null"
      (dragover)="onDragOver($event)"
      (dragleave)="over.set(false)"
      (drop)="onDrop($event)"
    >
      <input class="visually-hidden" type="file" [accept]="accept()" (change)="onChange($event)" />
      <span class="drop-kicker">{{ kicker() }}</span>
      <span class="drop-title">{{ title() }}</span>
      @if (file(); as f) {
        <span class="drop-file">{{ f.name }}</span>
        <span class="drop-hint">Cliquez pour changer de fichier</span>
      } @else {
        <span class="drop-hint">{{ hint() }}</span>
      }
    </label>
  `,
})
export class FileDrop {
  readonly kicker = input.required<string>();
  readonly title = input.required<string>();
  readonly hint = input('Glissez le fichier ici, ou cliquez pour le choisir');
  readonly accept = input('.txt,.csv,.tsv');
  readonly file = model<File | null>(null);
  /** Emitted only when the user picks a file, not when the parent sets one. */
  readonly picked = output<File>();
  protected readonly over = signal(false);

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.over.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.over.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.take(file);
    }
  }

  protected onChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.take(file);
    }
    input.value = '';
  }

  private take(file: File): void {
    this.file.set(file);
    this.picked.emit(file);
  }
}
