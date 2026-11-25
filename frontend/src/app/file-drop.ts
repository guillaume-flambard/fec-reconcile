import { Component, input, model, output, signal } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideFileCheck, lucideUpload } from '@ng-icons/lucide';

/** A large zone that takes one file, by drag and drop or by click. Spartan has no file input. */
@Component({
  selector: 'app-file-drop',
  imports: [NgIcon],
  providers: [provideIcons({ lucideUpload, lucideFileCheck })],
  host: { class: 'block min-w-0' },
  template: `
    <label
      class="group flex h-full min-h-40 cursor-pointer flex-col gap-1 rounded-xl border-2 p-5 transition-colors focus-within:ring-3 focus-within:ring-ring/50"
      [class]="
        over()
          ? 'border-solid border-primary bg-accent'
          : file()
            ? 'border-solid border-border bg-card'
            : 'border-dashed border-input bg-muted/40 hover:border-primary hover:bg-accent'
      "
      (dragover)="onDragOver($event)"
      (dragleave)="over.set(false)"
      (drop)="onDrop($event)"
    >
      <input class="sr-only" type="file" [accept]="accept()" (change)="onChange($event)" />
      <span class="font-display text-sm font-medium tracking-widest text-muted-foreground uppercase">
        {{ kicker() }}
      </span>
      <span class="font-display text-xl leading-tight font-medium">{{ title() }}</span>
      @if (file(); as f) {
        <span class="mt-auto flex items-center gap-2 font-medium break-all">
          <ng-icon name="lucideFileCheck" class="shrink-0 text-success" />
          {{ f.name }}
        </span>
        <span class="text-sm text-muted-foreground">Cliquez pour changer de fichier</span>
      } @else {
        <span class="mt-auto flex items-center gap-2 text-sm text-muted-foreground">
          <ng-icon name="lucideUpload" class="shrink-0" />
          {{ hint() }}
        </span>
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
