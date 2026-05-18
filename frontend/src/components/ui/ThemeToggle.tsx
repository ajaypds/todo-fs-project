import { Moon, Sun } from "lucide-react";
import { Button } from "./Button";
import { useThemeStore } from "../../store/themeStore";

export const ThemeToggle = () => {
  const { theme, toggleTheme } = useThemeStore();

  return (
    <Button
      variant="ghost"
      onClick={toggleTheme}
      className="w-10 h-10 p-0 rounded-xl"
    >
      {theme === "light" ? <Moon size={18} /> : <Sun size={18} />}
    </Button>
  );
};
