import type { ReactNode } from "react";

import { motion } from "framer-motion";

type Props = {
  children: ReactNode;
};

export const PageTransition = ({ children }: Props) => {
  return (
    <motion.div
      initial={{
        opacity: 0,
        y: 8,
      }}
      animate={{
        opacity: 1,
        y: 0,
      }}
      transition={{
        duration: 0.2,
      }}
    >
      {children}
    </motion.div>
  );
};
