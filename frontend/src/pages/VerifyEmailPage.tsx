import React, { useState, useEffect } from "react";
import { useSearchParams, useNavigate, Link } from "react-router-dom";
import toast from "react-hot-toast";
import { AuthLayout } from "../components/layout/AuthLayout";
import { Card } from "../components/ui/Card";
import { Input } from "../components/ui/Input";
import { Button } from "../components/ui/Button";
import { PageTransition } from "../components/ui/PageTransition";
import { useVerifyEmail, useResendVerification } from "../features/auth/authQueries";
import { useAuthStore } from "../store/authStore";
import { MailCheck, RefreshCw, Terminal, ArrowLeft } from "lucide-react";

export const VerifyEmailPage = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);

  const initialEmail = searchParams.get("email") || "";
  const [email, setEmail] = useState(initialEmail);
  const [code, setCode] = useState("");
  const [cooldown, setCooldown] = useState(0);

  const verifyMutation = useVerifyEmail();
  const resendMutation = useResendVerification();

  useEffect(() => {
    if (initialEmail) {
      setEmail(initialEmail);
    }
  }, [initialEmail]);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => {
      setCooldown((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  const handleVerify = (e: React.FormEvent) => {
    e.preventDefault();

    const cleanEmail = email.trim().toLowerCase();
    const cleanCode = code.trim();

    if (!cleanEmail) {
      toast.error("Please enter your email address.");
      return;
    }

    if (!cleanCode || cleanCode.length < 6) {
      toast.error("Please enter the complete 6-digit verification code.");
      return;
    }

    verifyMutation.mutate(
      { email: cleanEmail, code: cleanCode },
      {
        onSuccess: (response) => {
          if (response.token && response.refreshToken) {
            setAuth(response.token, response.refreshToken);
          }
          toast.success("Email verified successfully! Welcome to TodoFlow.");
          navigate("/");
        },
        onError: (error: any) => {
          const message =
            error.response?.data?.message ||
            "Invalid or expired verification code. Please try again.";
          toast.error(message);
        },
      }
    );
  };

  const handleResend = () => {
    const cleanEmail = email.trim().toLowerCase();
    if (!cleanEmail) {
      toast.error("Please enter your email address to resend the code.");
      return;
    }

    resendMutation.mutate(
      { email: cleanEmail },
      {
        onSuccess: () => {
          toast.success("A new verification code has been generated! Check console/terminal.");
          setCooldown(30);
        },
        onError: (error: any) => {
          const message =
            error.response?.data?.message ||
            "Failed to resend code. Please check your email and try again.";
          toast.error(message);
        },
      }
    );
  };

  return (
    <AuthLayout
      title="Verify your email"
      subtitle="Complete account setup to get started"
    >
      <PageTransition>
        <Card className="p-8">
          <div className="flex items-center justify-center w-12 h-12 mb-5 rounded-full bg-primary/10 text-primary mx-auto">
            <MailCheck className="w-6 h-6" />
          </div>

          <h1 className="text-2xl font-bold text-center mb-2">Check your inbox</h1>
          <p className="text-sm text-center text-muted-foreground mb-6">
            We sent a 6-digit verification code to{" "}
            <span className="font-medium text-foreground">{email || "your email"}</span>.
          </p>

          {/* Dev / Console notice banner */}
          <div className="mb-6 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3.5 text-xs text-amber-700 dark:text-amber-300 flex items-start gap-2.5">
            <Terminal className="w-4 h-4 mt-0.5 shrink-0 text-amber-600 dark:text-amber-400" />
            <div className="leading-relaxed">
              <strong className="font-semibold">Console Mode Active:</strong> Since SMTP
              email delivery is currently unconfigured, check the backend server
              terminal/console for your temporary 6-digit OTP code banner.
            </div>
          </div>

          <form onSubmit={handleVerify} className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-muted-foreground mb-1">
                Email Address
              </label>
              <Input
                type="email"
                placeholder="name@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={verifyMutation.isPending}
                required
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-muted-foreground mb-1">
                6-Digit Verification Code
              </label>
              <Input
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={6}
                placeholder="123456"
                className="text-center text-xl tracking-[0.4em] font-mono font-bold"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
                autoFocus
                disabled={verifyMutation.isPending}
                required
              />
            </div>

            <Button
              type="submit"
              disabled={verifyMutation.isPending || code.length < 6}
              className="w-full"
            >
              {verifyMutation.isPending ? "Verifying..." : "Verify & Continue"}
            </Button>

            <div className="flex items-center justify-between pt-2">
              <Button
                type="button"
                variant="ghost"
                className="text-xs px-2.5 py-1.5 h-auto text-muted-foreground hover:text-foreground"
                disabled={resendMutation.isPending || cooldown > 0}
                onClick={handleResend}
              >
                <RefreshCw
                  className={`w-3.5 h-3.5 mr-1.5 ${
                    resendMutation.isPending ? "animate-spin" : ""
                  }`}
                />
                {cooldown > 0
                  ? `Resend in ${cooldown}s`
                  : resendMutation.isPending
                  ? "Resending..."
                  : "Resend Code"}
              </Button>

              <Link
                to="/login"
                className="text-xs text-muted-foreground hover:text-primary transition inline-flex items-center"
              >
                <ArrowLeft className="w-3.5 h-3.5 mr-1" />
                Back to Sign In
              </Link>
            </div>
          </form>
        </Card>
      </PageTransition>
    </AuthLayout>
  );
};
