import { useState } from "react";

function App() {
  const [count, setCount] = useState(0);

  return (
    <div id="app">
      <div className="flex items-center justify-center">
        <div
          onClick={() => {
            setCount(count + 1);
          }}
          className="bg-amber-500 w-20 h-10 rounded-md flex items-center justify-center cursor-pointer select-none hover:bg-amber-600 transition-colors active:bg-amber-400"
        >
          Count: {count}
        </div>
      </div>
    </div>
  );
}

export default App;
