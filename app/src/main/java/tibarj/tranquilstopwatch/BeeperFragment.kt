package tibarj.tranquilstopwatch

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import tibarj.tranquilstopwatch.databinding.BeeperFragmentBinding


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class BeeperFragment : Fragment() {
    private val tag: String = "BeeperFragment"
    private var _binding: BeeperFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(tag, "onCreateView")
        _binding = BeeperFragmentBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}