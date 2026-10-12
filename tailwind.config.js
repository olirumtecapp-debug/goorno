/** @type {import('tailwindcss').Config} */
export default {
  content: ["./client/index.html", "./client/src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        goorno: {
          dark: "#073B32",
          darker: "#04241E",
          surface: "#0A463B",
          lime: "#7C9303",
          limeBright: "#97B304",
        },
      },
    },
  },
  plugins: [],
};
