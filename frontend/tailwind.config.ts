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
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
        popover: {
          DEFAULT: "hsl(var(--popover))",
          foreground: "hsl(var(--popover-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },

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
        "fade-in-up": {
          "0%": { opacity: "0", transform: "translateY(14px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        "fade-in-right": {
          "0%": { opacity: "0", transform: "translateX(18px)" },
          "100%": { opacity: "1", transform: "translateX(0)" },
        },
        "fade-in": {
          "0%": { opacity: "0" },
          "100%": { opacity: "1" },
        },
        float: {
          "0%, 100%": { transform: "translate(0, 0)" },
          "50%": { transform: "translate(14px, -22px)" },
        },
        "float-slow": {
          "0%, 100%": { transform: "translate(0, 0)" },
          "50%": { transform: "translate(-18px, 16px)" },
        },
        "gradient-x": {
          "0%, 100%": { backgroundPosition: "0% 50%" },
          "50%": { backgroundPosition: "100% 50%" },
        },
        shimmer: {
          "0%": { transform: "translateX(-120%)" },
          "100%": { transform: "translateX(220%)" },
        },
        "glow-pulse": {
          "0%, 100%": { opacity: "0.55", transform: "scale(1)" },
          "50%": { opacity: "0.15", transform: "scale(1.8)" },
        },
        "flash-highlight": {
          "0%": { backgroundColor: "rgba(255,255,255,0.16)" },
          "100%": { backgroundColor: "rgba(255,255,255,0)" },
        },
      },
      animation: {
        "fade-scale-in": "fade-scale-in 180ms ease-out",
        "accordion-down": "accordion-down 200ms ease-out",
        "accordion-up": "accordion-up 200ms ease-out",
        "fade-in-up": "fade-in-up 0.7s cubic-bezier(0.16,1,0.3,1) both",
        "fade-in-right": "fade-in-right 0.5s cubic-bezier(0.16,1,0.3,1) both",
        "fade-in": "fade-in 0.6s ease-out both",
        float: "float 8s ease-in-out infinite",
        "float-slow": "float-slow 12s ease-in-out infinite",
        "gradient-x": "gradient-x 10s ease infinite",
        shimmer: "shimmer 2.2s ease-in-out infinite",
        "glow-pulse": "glow-pulse 2.2s ease-in-out infinite",
        "flash-highlight": "flash-highlight 1.8s ease-out",
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
};

export default config;
