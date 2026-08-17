export interface SectionFormProps<T> {
  content: T;
  onChange: (updater: (prev: T) => T) => void;
  readOnly: boolean;
}
