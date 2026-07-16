import { Directive, ElementRef, HostListener, Optional, Self } from '@angular/core';
import { NgControl } from '@angular/forms';
import { arabicToLatin, hasArabic } from '../utils/transliterate';

/**
 * Attach to any name <input> to auto-convert Arabic typed/pasted into it to a
 * Latin spelling. Works with both template-driven ([(ngModel)]) and reactive
 * (formControlName) inputs via the optionally-injected NgControl.
 *
 * Usage:  <input appLatinName formControlName="firstName" />
 */
@Directive({ selector: '[appLatinName]' })
export class LatinNameDirective {
  constructor(
    private el: ElementRef<HTMLInputElement>,
    @Optional() @Self() private ngControl: NgControl
  ) {}

  @HostListener('input')
  @HostListener('change')
  onInput(): void {
    const current = this.el.nativeElement.value;
    if (!hasArabic(current)) return;

    const converted = arabicToLatin(current);
    if (converted === current) return;

    this.el.nativeElement.value = converted;
    // Keep the bound model/control in sync (covers ngModel and formControlName).
    if (this.ngControl?.control) {
      this.ngControl.control.setValue(converted);
    }
  }
}
