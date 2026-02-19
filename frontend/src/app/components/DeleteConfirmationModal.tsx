import { motion, AnimatePresence } from "motion/react";
import { AlertCircle, X, Trash2 } from "lucide-react";

interface DeleteConfirmationModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  roomName: string;
}

export function DeleteConfirmationModal({ isOpen, onClose, onConfirm, roomName }: DeleteConfirmationModalProps) {
  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 bg-black/80 backdrop-blur-sm z-[100] flex items-center justify-center p-4"
          onClick={onClose}
        >
          <motion.div
            initial={{ scale: 0.95, opacity: 0, y: 10 }}
            animate={{ scale: 1, opacity: 1, y: 0 }}
            exit={{ scale: 0.95, opacity: 0, y: 10 }}
            onClick={(e) => e.stopPropagation()}
            className="bg-zinc-900 border border-zinc-800 rounded-xl p-6 w-full max-w-sm shadow-2xl relative overflow-hidden"
          >
            <div className="flex flex-col items-center text-center space-y-4">
              <div className="bg-red-500/10 p-4 rounded-full border border-red-500/20 shadow-[0_0_20px_rgba(239,68,68,0.2)] animate-pulse">
                <AlertCircle className="text-red-500 w-8 h-8" strokeWidth={2.5} />
              </div>
              
              <h3 className="text-xl font-bold text-white tracking-tight">
                Delete Chat?
              </h3>
              
              <div className="space-y-2">
                <p className="text-zinc-400 text-sm">
                  Are you sure you want to delete <span className="text-white font-medium">"{roomName}"</span>?
                </p>
                <p className="text-red-400 text-xs font-semibold uppercase tracking-wider bg-red-500/5 py-1 px-2 rounded inline-block border border-red-500/10">
                  This action cannot be undone.
                </p>
              </div>

              <div className="flex gap-3 w-full pt-4">
                <button
                  onClick={onClose}
                  className="flex-1 py-2.5 px-4 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-zinc-300 font-medium transition-colors border border-white/5"
                >
                  Cancel
                </button>
                <button
                  onClick={() => {
                    onConfirm();
                    onClose();
                  }}
                  className="flex-1 py-2.5 px-4 rounded-lg bg-red-600 hover:bg-red-500 text-white font-bold transition-all shadow-lg shadow-red-900/20 flex items-center justify-center gap-2 group"
                >
                  <Trash2 size={16} className="group-hover:scale-110 transition-transform" />
                  Delete
                </button>
              </div>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
