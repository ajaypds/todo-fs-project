import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate } from "react-router-dom";
import { AuthLayout } from "../components/layout/AuthLayout";
import { Card } from "../components/ui/Card";
import { Input } from "../components/ui/Input";
import { Button } from "../components/ui/Button";
import { useRegister } from "../features/auth/authQueries";
import { PageTransition } from "../components/ui/PageTransition";

const schema = z.object({
  fullName: z.string().min(2),
  email: z.email(),
  password: z.string().min(6),
});

type FormValues = z.infer<typeof schema>;

export const RegisterPage = () => {
  const navigate = useNavigate();

  const registerMutation = useRegister();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  const onSubmit = (values: FormValues) => {
    registerMutation.mutate(values, {
      onSuccess: () => {
        navigate("/login");
      },
      onError: () => {
        alert("Registration failed");
      },
    });
  };

  return (
    <AuthLayout title="Create account" subtitle="Start organizing your work">
      <PageTransition>
        <Card className="p-8">
          <form
            onSubmit={handleSubmit(onSubmit)}
            // className="bg-white p-8 rounded-lg shadow-md w-96"
          >
            <h1 className="text-2xl font-bold mb-6">Register</h1>

            <div className="mb-4">
              <Input
                placeholder="Full Name"
                // className="w-full border p-3 rounded"
                {...register("fullName")}
              />

              {errors.fullName && (
                <p className="text-red-500 text-sm">
                  {errors.fullName.message}
                </p>
              )}
            </div>

            <div className="mb-4">
              <Input
                placeholder="Email"
                // className="w-full border p-3 rounded"
                {...register("email")}
              />

              {errors.email && (
                <p className="text-red-500 text-sm">{errors.email.message}</p>
              )}
            </div>

            <div className="mb-4">
              <Input
                type="password"
                placeholder="Password"
                // className="w-full border p-3 rounded"
                {...register("password")}
              />

              {errors.password && (
                <p className="text-red-500 text-sm">
                  {errors.password.message}
                </p>
              )}
            </div>

            <Button
              type="submit"
              disabled={registerMutation.isPending}
              className="w-full"
            >
              {registerMutation.isPending ? "Registering..." : "Register"}
            </Button>
          </form>
        </Card>
      </PageTransition>
    </AuthLayout>
  );
};
