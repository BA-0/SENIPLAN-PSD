export interface SectionFormProps<T> {
  content: T;
  onChange: (updater: (prev: T) => T) => void;
  readOnly: boolean;
  /** Admin et direction generale : renommer les axes repris de S08 depuis la section. */
  canEditAxes?: boolean;
}
