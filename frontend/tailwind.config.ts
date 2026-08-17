import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: ["class"],
  content: [
    "./src/pages/**/*.{ts,tsx}",
    "./src/components/**/*.{ts,tsx}",
    "./src/app/**/*.{ts,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ["var(--font-inter)", "system-ui", "-apple-system", "sans-serif"],
      },
      colors: {
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",

        primary: {
          50: "#EEF7F1",
          100: "#D6ECDD",
          200: "#ADD9BB",
          300: "#7FC297",
          400: "#52A470",
          500: "#2D7A45",
          600: "#266A3B",
          700: "#1F5731",
          800: "#184427",
          900: "#11301C",
          DEFAULT: "#2D7A45",
          foreground: "#FFFFFF",
        },
        accent: {
          50: "#FDEDEE",
          100: "#FBD4D6",
          200: "#F7ABAF",
          500: "#EC1D25",
          600: "#D01118",
          700: "#A80E14",
          DEFAULT: "#EC1D25",
          foreground: "#FFFFFF",
        },
        status: {
          notStarted: { bg: "#F1F5F9", text: "#64748B" },
          inProgress: { bg: "#EFF6FF", text: "#1D4ED8" },
          submitted: { bg: "#EEF7F1", text: "#1F5731" },
          validated: { bg: "#2D7A45", text: "#FFFFFF" },
          revision: { bg: "#FFF7ED", text: "#C2410C" },
          criticalHigh: { bg: "#FBD4D6", text: "#A80E14" },
          criticalMedium: { bg: "#FEF3C7", text: "#B45309" },
          criticalLow: { bg: "#D6ECDD", text: "#1F5731" },
        },
      },
      borderRadius: {
        lg: "0.5rem",
        xl: "0.75rem",
      },
      boxShadow: {
        sm: "0 1px 2px 0 rgb(0 0 0 / 0.05)",
        md: "0 4px 10px -2px rgb(0 0 0 / 0.08)",
      },
      keyframes: {
        "fade-scale-in": {
          "0%": { opacity: "0", transform: "scale(0.97)" },
          "100%": { opacity: "1", transform: "scale(1)" },
        },
        "accordion-down": {
          from: { height: "0" },
          to: { height: "var(--radix-accordion-content-height)" },
        },
        "accordion-up": {
          from: { height: "var(--radix-accordion-content-height)" },
          to: { height: "0" },
        },
      },
      animation: {
        "fade-scale-in": "fade-scale-in 180ms ease-out",
        "accordion-down": "accordion-down 200ms ease-out",
        "accordion-up": "accordion-up 200ms ease-out",
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
};

export default config;
